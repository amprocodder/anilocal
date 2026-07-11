package com.anilocal.app.data.download

import android.net.Uri
import com.anilocal.app.data.local.DownloadDao
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The request headers the download stack fetches with, keyed by the request URL's HOST. Consulted
 * per-request by the ResolvingDataSource wrapped around the download HTTP factory (see
 * DownloadModule), so it works in EVERY process that runs downloads — including a headless
 * scheduler/boot service restart where no ViewModel (and thus no DownloadRepositoryImpl) ever
 * exists. On first use it lazily restores every active download's persisted headers from Room,
 * keyed by that download's stream host; enqueue/resume/retry set the relevant host's headers.
 *
 * Per-host (not one global map) because per-title source selection legitimately puts two concurrent
 * downloads on different sources — and thus different hosts, each needing its own Referer. A request
 * to a host we've never seen (e.g. an HLS CDN segment on a different host than the manifest) falls
 * back to the most-recently-set headers, preserving the old single-value behavior for that case.
 */
@Singleton
class DownloadHeaderStore @Inject constructor(
    private val dao: DownloadDao,
    moshi: Moshi,
) {
    private val adapter = moshi.adapter<Map<String, String>>(
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    )

    private val byHost = ConcurrentHashMap<String, Map<String, String>>()

    @Volatile
    private var newest: Map<String, String>? = null   // cross-host fallback (last explicitly set)

    @Volatile
    private var restored = false

    fun set(host: String?, headers: Map<String, String>) {
        newest = headers
        host?.takeIf { it.isNotEmpty() }?.let { byHost[it] = headers }
    }

    fun setFromJson(host: String?, json: String?) {
        set(host, json?.let { runCatching { adapter.fromJson(it) }.getOrNull() }.orEmpty())
    }

    /** Called on Media3's download threads (blocking Room I/O is fine there). */
    fun current(host: String?): Map<String, String> {
        ensureRestored()
        host?.let { byHost[it] }?.let { return it }
        return newest ?: emptyMap()
    }

    /** Lazily seed byHost from Room ONCE — without clobbering anything an explicit set() already put
     *  there (an in-process set is always more authoritative than the persisted row). */
    @Synchronized
    private fun ensureRestored() {
        if (restored) return
        restored = true
        runCatching {
            val rows = dao.activeNow()   // newest first
            rows.forEach { row ->
                val host = row.streamUri.toHost() ?: return@forEach
                if (byHost.containsKey(host)) return@forEach
                row.headersJson.parse().takeIf { it.isNotEmpty() }?.let { byHost[host] = it }
            }
            if (newest == null) newest = rows.firstOrNull()?.headersJson.parse()
        }
    }

    private fun String?.parse(): Map<String, String> =
        this?.let { runCatching { adapter.fromJson(it) }.getOrNull() }.orEmpty()

    private fun String.toHost(): String? = runCatching { Uri.parse(this).host }.getOrNull()
}
