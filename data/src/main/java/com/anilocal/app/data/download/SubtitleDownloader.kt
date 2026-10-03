package com.anilocal.app.data.download

import com.anilocal.app.domain.model.Subtitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class DownloadedSubtitle(val subtitle: Subtitle, val file: File)

/** Sidecar work stays off the UI thread and shares one bounded pool across enqueue operations. */
internal class SubtitleDownloader(client: OkHttpClient, private val directory: File) {
    private val client = client.newBuilder()
        .callTimeout(12, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    private val permits = Semaphore(3)

    suspend fun downloadAll(
        subtitles: List<Subtitle>,
        id: String,
        headers: Map<String, String>,
    ): List<DownloadedSubtitle> = withContext(Dispatchers.IO) {
        val completed = ConcurrentLinkedQueue<File>()
        try {
            coroutineScope {
                subtitles.mapIndexed { index, sub ->
                    async {
                        permits.withPermit {
                            try {
                                val file = download(sub, id, index, headers)
                                completed.add(file)
                                DownloadedSubtitle(sub, file)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                // A missing sidecar must not prevent the video download.
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        } catch (error: Throwable) {
            completed.forEach { it.delete() }
            throw error
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun download(
        sub: Subtitle,
        id: String,
        index: Int,
        headers: Map<String, String>,
    ): File = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(sub.url)
            .apply { headers.forEach { (name, value) -> runCatching { header(name, value) } } }.build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation {
            call.cancel()
            // Cancel the transport; the callback owns closing the response after its read ends.
        }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                var temporary: File? = null
                var saved: File? = null
                var delivered = false
                try {
                    response.use {
                        if (!continuation.isActive) return
                        if (!response.isSuccessful) throw IOException("Subtitle HTTP ${response.code}")
                        val body = response.body ?: throw IOException("Empty subtitle response")
                        if (body.contentLength() > MAX_BYTES) throw IOException("Subtitle is too large")
                        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create subtitle directory")
                        val extension = sub.url.substringBefore('?').substringBefore('#')
                            .substringAfterLast('.', "sub").lowercase(Locale.ROOT)
                            .takeIf { it in setOf("vtt", "srt", "ass", "ssa", "ttml", "dfxp") } ?: "sub"
                        val file = File(directory, "$id-$index.$extension")
                        temporary = File.createTempFile("$id-$index-", ".part", directory)
                        body.byteStream().use { input ->
                            temporary!!.outputStream().use { output ->
                                val buffer = ByteArray(8_192)
                                var total = 0L
                                while (continuation.isActive) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    if (total > MAX_BYTES) throw IOException("Subtitle is too large")
                                    output.write(buffer, 0, count)
                                }
                                if (!continuation.isActive) return
                            }
                        }
                        if (!temporary!!.renameTo(file)) throw IOException("Cannot save subtitle")
                        saved = file
                        delivered = true
                        continuation.resume(file) { file.delete() }
                    }
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                } finally {
                    temporary?.delete()
                    if (!delivered) saved?.delete()
                }
            }
        })
    }

    private companion object {
        const val MAX_BYTES = 4L * 1_024 * 1_024
    }
}
