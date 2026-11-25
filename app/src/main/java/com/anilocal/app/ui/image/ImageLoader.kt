package com.anilocal.app.ui.image

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.max

/**
 * The app's image loader: posters, banners and episode thumbnails, for [RemoteImage].
 *
 * - **Memory**: decoded bitmaps in an LRU keyed by URL, sized like Coil's default (a fifth of the
 *   app's memory class). A hit is checked synchronously on the main thread, so an image scrolled back
 *   into view draws in the same frame.
 * - **Disk**: the original bytes, served regardless of cache headers — once seen, an image renders
 *   offline. It reads and writes Coil's file layout, so everything Coil cached is still used.
 * - **Network**: the shared OkHttp client (its HTTP cache off, since this one owns the bytes);
 *   concurrent requests for one URL share a single download, cancelled once nobody wants it.
 * - **Decode**: off the main thread, downsampled to the size it's drawn at, hardware-backed where
 *   the platform allows (as Coil did) so pixels live in graphics memory, not the Java heap.
 *
 * Failures are not cached: an image that fails to load is simply retried the next time it's shown.
 */
class ImageLoader(context: Context, client: OkHttpClient) {

    /** Implemented by the Application so composables can reach the one loader. */
    interface Provider {
        val imageLoader: ImageLoader
    }

    private class Decoded(bitmap: Bitmap, val sampled: Boolean) {
        val image: ImageBitmap = bitmap.asImageBitmap()
        val bytes: Int = bitmap.allocationByteCount
    }

    private val memory = object : LruCache<String, Decoded>(memoryCacheBytes(context)) {
        override fun sizeOf(key: String, value: Decoded): Int = value.bytes
    }
    private val disk = ImageDiskCache(File(context.cacheDir, "image_cache"), DISK_CACHE_BYTES)
    private val client = client.newBuilder().cache(null).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(DECODE_PARALLELISM)

    private class Download(val result: Deferred<File?>) {
        var waiters = 0
    }
    private val downloads = HashMap<String, Download>()

    /** A memory-cached image good enough to draw [url] at [size] with [scale], or null. Cheap. */
    fun peek(url: String, size: IntSize, scale: ContentScale): ImageBitmap? {
        val hit = memory.get(url) ?: return null
        return hit.image.takeIf { !hit.sampled || sharpEnough(it, size, scale) }
    }

    /** Loads [url] for drawing at [size] with [scale]; null if it can't be fetched or decoded. */
    suspend fun load(url: String, size: IntSize, scale: ContentScale): ImageBitmap? {
        peek(url, size, scale)?.let { return it }
        val local = localFile(url)
        val file = local ?: withContext(Dispatchers.IO) { disk.get(url) } ?: download(url) ?: return null
        val decoded = withContext(decodeDispatcher) {
            runCatching { decode(file, size, scale) }.getOrNull()
        }
        if (decoded == null) {
            // A cached file that won't decode is corrupt (or a non-image error page): drop it so the
            // next attempt fetches it again instead of failing forever.
            if (local == null) withContext(Dispatchers.IO) { disk.remove(url) }
            return null
        }
        val current = memory.get(url)
        if (current == null || current.sampled && decoded.image.width > current.image.width) memory.put(url, decoded)
        return decoded.image
    }

    /** Mirrors Coil's response to memory pressure: halve the cache when running low, empty it in the background. */
    fun trimMemory(level: Int) {
        @Suppress("DEPRECATION")
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> memory.evictAll()
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW &&
                level < ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> memory.trimToSize(memory.size() / 2)
        }
    }

    private fun localFile(url: String): File? = when {
        url.startsWith("file://") -> File(url.removePrefix("file://"))
        url.startsWith("/") -> File(url)
        else -> null
    }?.takeIf { it.isFile }

    /** One shared download per URL; the last waiter to give up cancels it. */
    private suspend fun download(url: String): File? {
        val shared = synchronized(downloads) {
            downloads.getOrPut(url) {
                Download(scope.async(start = CoroutineStart.LAZY) { fetch(url) })
            }.also { it.waiters++ }
        }
        shared.result.start()
        try {
            return shared.result.await()
        } finally {
            synchronized(downloads) {
                if (--shared.waiters == 0) {
                    if (!shared.result.isCompleted) shared.result.cancel()
                    if (downloads[url] === shared) downloads.remove(url)
                }
            }
        }
    }

    private suspend fun fetch(url: String): File? {
        val request = runCatching { Request.Builder().url(url).build() }.getOrNull() ?: return null
        return try {
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) return null
                disk.put(url, response.body.source())
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun decode(file: File, size: IntSize, scale: ContentScale): Decoded? =
        if (Build.VERSION.SDK_INT >= 28) decodeWithImageDecoder(file, size, scale)
        else decodeWithBitmapFactory(file, size, scale)

    @RequiresApi(28)
    private fun decodeWithImageDecoder(file: File, size: IntSize, scale: ContentScale): Decoded {
        var sampled = false
        // The default allocator picks a hardware bitmap where it can.
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val target = targetSize(info.size.width, info.size.height, size, scale)
            if (target.width < info.size.width) {
                decoder.setTargetSize(target.width, target.height)
                sampled = true
            }
        }
        return Decoded(bitmap, sampled)
    }

    private fun decodeWithBitmapFactory(file: File, size: IntSize, scale: ContentScale): Decoded? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        val target = targetSize(options.outWidth, options.outHeight, size, scale)
        var sample = 1
        while (options.outWidth / (sample * 2) >= target.width && options.outHeight / (sample * 2) >= target.height) {
            sample *= 2
        }
        options.inJustDecodeBounds = false
        options.inSampleSize = sample
        if (Build.VERSION.SDK_INT >= 26) options.inPreferredConfig = Bitmap.Config.HARDWARE
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
        if (Build.VERSION.SDK_INT < 26 || bitmap.config != Bitmap.Config.HARDWARE) bitmap.prepareToDraw()
        return Decoded(bitmap, sample > 1)
    }

    private companion object {
        const val DISK_CACHE_BYTES = 256L * 1024 * 1024
        const val DECODE_PARALLELISM = 3
        /** How much a cached, downsampled image may be enlarged before it's decoded again at the bigger size. */
        const val UPSCALE_TOLERANCE = 1.05f

        fun memoryCacheBytes(context: Context): Int {
            val am = context.getSystemService(ActivityManager::class.java)
            val fraction = if (am?.isLowRamDevice == true) 0.15 else 0.2
            val classMb = am?.memoryClass ?: 256
            return (classMb * fraction * 1024 * 1024).toInt()
        }

        /**
         * The smallest size the image can be decoded at and still be drawn at [size] with [scale]
         * without being enlarged — the original size when it would be shown at or above it.
         */
        fun targetSize(width: Int, height: Int, size: IntSize, scale: ContentScale): IntSize {
            if (size.width <= 0 || size.height <= 0) return IntSize(width, height)
            val factor = scale.computeScaleFactor(
                Size(width.toFloat(), height.toFloat()),
                Size(size.width.toFloat(), size.height.toFloat()),
            )
            val s = max(factor.scaleX, factor.scaleY)
            if (s >= 1f) return IntSize(width, height)
            return IntSize(ceil(width * s).toInt().coerceAtLeast(1), ceil(height * s).toInt().coerceAtLeast(1))
        }

        fun sharpEnough(image: ImageBitmap, size: IntSize, scale: ContentScale): Boolean {
            if (size.width <= 0 || size.height <= 0) return true
            val factor = scale.computeScaleFactor(
                Size(image.width.toFloat(), image.height.toFloat()),
                Size(size.width.toFloat(), size.height.toFloat()),
            )
            return max(factor.scaleX, factor.scaleY) <= UPSCALE_TOLERANCE
        }

        suspend fun Call.await(): Response = suspendCancellableCoroutine { cont: CancellableContinuation<Response> ->
            cont.invokeOnCancellation { runCatching { cancel() } }
            enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response) { _, value, _ -> value.close() }
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (!cont.isCancelled) cont.resumeWith(Result.failure(e))
                }
            })
        }
    }
}
