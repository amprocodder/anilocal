package com.anilocal.app.ui.image

import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException

/**
 * Original image bytes on disk, bounded by [maxBytes] and evicted least-recently-used first.
 *
 * It keeps **Coil 2's file layout** — the bytes in `<sha256(url) hex>.1` (Coil's `.0` metadata and
 * `journal` are left alone and evicted along with their entry) — so an upgrade keeps every image the
 * old loader cached, and they still render offline. Recency is the file's modification time, bumped
 * at most once a day on read; files are written to a temp name and renamed, so a reader never sees a
 * half-written image.
 */
internal class ImageDiskCache(private val dir: File, private val maxBytes: Long) {

    private val lock = Any()
    /** Bytes on disk; -1 until the first write scans the directory. */
    private var size = -1L

    fun get(url: String): File? {
        val file = File(dir, key(url) + DATA)
        if (!file.isFile) return null
        val now = System.currentTimeMillis()
        if (now - file.lastModified() > TOUCH_INTERVAL_MS) file.setLastModified(now)
        return file
    }

    /** Streams [source] into the cache; the stored file, or null if it couldn't be written. */
    fun put(url: String, source: BufferedSource): File? {
        val key = key(url)
        dir.mkdirs()
        val tmp = File(dir, "$key.${Thread.currentThread().id}$TEMP")
        try {
            tmp.sink().buffer().use { it.writeAll(source) }
            val file = File(dir, key + DATA)
            val previous = file.length()
            if (!tmp.renameTo(file)) throw IOException("rename failed")
            synchronized(lock) {
                if (size >= 0) size += file.length() - previous
            }
            trimIfNeeded()
            return file
        } catch (e: IOException) {
            tmp.delete()
            return null
        }
    }

    fun remove(url: String) {
        val key = key(url)
        val removed = listOf(File(dir, key + DATA), File(dir, key + META)).sumOf { f -> f.length().also { f.delete() } }
        synchronized(lock) { if (size >= 0) size -= removed }
    }

    private fun trimIfNeeded() {
        synchronized(lock) {
            if (size < 0) size = scan()
            if (size <= maxBytes) return
            val entries = dir.listFiles { f -> f.name.endsWith(DATA) }.orEmpty().sortedBy { it.lastModified() }
            for (data in entries) {
                if (size <= maxBytes * TRIM_TO) break
                val meta = File(dir, data.name.removeSuffix(DATA) + META)
                size -= data.length() + meta.length()
                data.delete()
                meta.delete()
            }
        }
    }

    /** Totals the cache and clears temp files a crash left behind. */
    private fun scan(): Long {
        val staleBefore = System.currentTimeMillis() - STALE_TEMP_MS
        return dir.listFiles().orEmpty().sumOf { f ->
            if (f.name.endsWith(TEMP) || f.name.endsWith(".tmp")) {
                if (f.lastModified() < staleBefore) f.delete()
                0L
            } else if (f.name.endsWith(DATA) || f.name.endsWith(META)) f.length() else 0L
        }
    }

    private companion object {
        const val DATA = ".1"
        const val META = ".0"
        const val TEMP = ".part"
        const val TOUCH_INTERVAL_MS = 24L * 60 * 60 * 1000
        const val STALE_TEMP_MS = 60L * 60 * 1000
        const val TRIM_TO = 0.9

        /** Coil 2's disk key: the SHA-256 of the URL, in hex. */
        fun key(url: String): String = url.encodeUtf8().sha256().hex()
    }
}
