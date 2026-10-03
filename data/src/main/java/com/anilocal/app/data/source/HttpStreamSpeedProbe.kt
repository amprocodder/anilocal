package com.anilocal.app.data.source

import com.anilocal.app.domain.model.VideoStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A fresh, bounded measurement. Null means the URL could not supply usable media bytes. */
internal fun interface StreamSpeedProbe {
    suspend fun bytesPerSecond(stream: VideoStream): Double?
}

internal class HttpStreamSpeedProbe(client: OkHttpClient) : StreamSpeedProbe {
    // Retain the injected client's connections/interceptors, but never reuse a cached speed sample.
    private val probeClient = client.newBuilder()
        .cache(null)
        .callTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .connectTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun bytesPerSecond(stream: VideoStream): Double? = try {
        withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            val url = stream.url.toHttpUrlOrNull() ?: return@withTimeoutOrNull null
            val hls = stream.mimeType?.contains("mpegurl", ignoreCase = true) == true ||
                url.encodedPath.endsWith(".m3u8", ignoreCase = true)
            val first = readSample(url, stream.headers, if (hls) MAX_MANIFEST_BYTES + 1 else MAX_MEDIA_BYTES)
            if (hls || first.isManifest()) {
                probePlaylist(first, stream)
            } else {
                first.mediaSpeed()
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private suspend fun probePlaylist(first: Sample, stream: VideoStream): Double? {
        var playlistUrl = first.responseUrl
        var sample = first
        // Master -> variant -> media playlist. Reject recursive/unbounded playlist chains.
        repeat(MAX_PLAYLIST_DEPTH) {
            if (sample.bytes.size > MAX_MANIFEST_BYTES) return null
            val lines = sample.bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            if (lines.firstOrNull() != "#EXTM3U") return null
            val variants = lines.mapIndexedNotNull { index, line ->
                if (!line.startsWith("#EXT-X-STREAM-INF:")) return@mapIndexedNotNull null
                val uri = lines.drop(index + 1).firstOrNull { !it.startsWith("#") }
                    ?: return@mapIndexedNotNull null
                val height = Regex("RESOLUTION=\\d+x(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val bandwidth = Regex("(?:^|[:,])BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                PlaylistVariant(uri, height, bandwidth)
            }
            if (variants.isNotEmpty()) {
                val targetHeight = stream.height
                val variant = if (targetHeight != null) {
                    variants.minWith(compareBy<PlaylistVariant> { kotlin.math.abs(it.height - targetHeight) }
                        .thenByDescending { it.bandwidth })
                } else {
                    variants.maxWith(compareBy<PlaylistVariant> { it.height }.thenBy { it.bandwidth })
                }
                playlistUrl = playlistUrl.resolve(variant.uri) ?: return null
                sample = readSample(playlistUrl, stream.headers, MAX_MANIFEST_BYTES + 1)
                playlistUrl = sample.responseUrl
            } else {
                // Measure a real media segment, never the tiny playlist itself.
                val segmentIndex = lines.indexOfFirst { !it.startsWith("#") }
                if (segmentIndex < 0) return null
                val segmentUrl = playlistUrl.resolve(lines[segmentIndex]) ?: return null
                val byteRange = lines.take(segmentIndex).lastOrNull { it.startsWith("#EXT-X-BYTERANGE:") }
                    ?.substringAfter(':')?.split('@')
                val segmentSize = byteRange?.firstOrNull()?.toLongOrNull()
                val offset = byteRange?.getOrNull(1)?.toLongOrNull() ?: 0L
                if (offset < 0 || (segmentSize != null && segmentSize <= 0)) return null
                val count = minOf(MAX_MEDIA_BYTES.toLong(), segmentSize ?: MAX_MEDIA_BYTES.toLong()).toInt()
                return readSample(segmentUrl, stream.headers, count, offset).mediaSpeed()
            }
        }
        return null
    }

    private suspend fun readSample(
        url: HttpUrl,
        headers: Map<String, String>,
        maxBytes: Int,
        offset: Long = 0,
    ): Sample = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
            header("Range", "bytes=$offset-${offset + maxBytes - 1}")
            header("Accept-Encoding", "identity")
            header("Cache-Control", "no-cache")
        }.build()
        val call = probeClient.newCall(request)
        val activeResponse = AtomicReference<Response?>(null)
        val started = System.nanoTime()
        continuation.invokeOnCancellation {
            call.cancel()
            // A body may be in a buffered read on another thread; cancellation cleanup cannot
            // throw into the coroutine's completion handlers if that read is closing it too.
            runCatching { activeResponse.get()?.close() }
        }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                activeResponse.set(response)
                try {
                    response.use {
                        if (!continuation.isActive) return
                        if (!response.isSuccessful) throw IOException("Media probe HTTP ${response.code}")
                        val body = response.body ?: throw IOException("Empty media response")
                        val output = ByteArrayOutputStream(minOf(maxBytes, 16_384))
                        val buffer = ByteArray(8_192)
                        body.byteStream().use { input ->
                            while (output.size() < maxBytes && continuation.isActive) {
                                val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - output.size()))
                                if (read == -1) break
                                output.write(buffer, 0, read)
                            }
                        }
                        val sample = Sample(
                            output.toByteArray(),
                            (System.nanoTime() - started).coerceAtLeast(1),
                            body.contentType()?.toString().orEmpty(),
                            response.request.url,
                        )
                        if (continuation.isActive) continuation.resume(sample)
                    }
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                } finally {
                    activeResponse.set(null)
                }
            }
        })
    }

    private data class PlaylistVariant(val uri: String, val height: Int, val bandwidth: Long)

    private data class Sample(
        val bytes: ByteArray,
        val elapsedNanos: Long,
        val contentType: String,
        val responseUrl: HttpUrl,
    ) {
        fun isManifest(): Boolean = contentType.contains("mpegurl", ignoreCase = true) ||
            bytes.take(16).toByteArray().toString(Charsets.UTF_8).removePrefix("\uFEFF").trimStart().startsWith("#EXTM3U")

        fun mediaSpeed(): Double? {
            if (bytes.size < MIN_MEDIA_BYTES || isManifest() ||
                contentType.startsWith("text/", ignoreCase = true) ||
                contentType.contains("json", ignoreCase = true)) return null
            return bytes.size * 1_000_000_000.0 / elapsedNanos
        }
    }

    private companion object {
        const val MAX_MEDIA_BYTES = 256 * 1_024
        const val MAX_MANIFEST_BYTES = 64 * 1_024
        const val MIN_MEDIA_BYTES = 1_024
        const val MAX_PLAYLIST_DEPTH = 3
        const val REQUEST_TIMEOUT_MS = 3_000L
        const val PROBE_TIMEOUT_MS = 5_000L
    }
}
