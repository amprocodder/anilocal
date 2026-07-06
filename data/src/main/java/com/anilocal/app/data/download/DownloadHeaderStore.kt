package com.anilocal.app.data.download

import com.anilocal.app.data.local.DownloadDao
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The request headers the download stack fetches with. Consulted per-request by the
 * ResolvingDataSource wrapped around the download HTTP factory (see DownloadModule), so it works
 * in EVERY process that runs downloads — including a headless scheduler/boot service restart where
 * no ViewModel (and thus no DownloadRepositoryImpl) ever exists. On first use it lazily restores
 * the newest active download's persisted headers from Room; enqueue/resume overwrite it with the
 * relevant download's headers.
 *
 * One store-wide map is a known limit: concurrent downloads from two different sources share it,
 * last-set wins (headers are per-source — Referer etc. — so same-source season downloads are fine).
 */
@Singleton
class DownloadHeaderStore @Inject constructor(
    private val dao: DownloadDao,
    moshi: Moshi,
) {
    private val adapter = moshi.adapter<Map<String, String>>(
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    )

    @Volatile
    private var headers: Map<String, String>? = null   // null = not restored yet

    fun set(headers: Map<String, String>) {
        this.headers = headers
    }

    fun setFromJson(json: String?) {
        headers = json?.let { runCatching { adapter.fromJson(it) }.getOrNull() }.orEmpty()
    }

    /** Called on Media3's download threads (blocking Room I/O is fine there). */
    fun current(): Map<String, String> = headers ?: restore()

    @Synchronized
    private fun restore(): Map<String, String> {
        headers?.let { return it }
        val restored = runCatching {
            dao.newestActiveNow()?.headersJson?.let { adapter.fromJson(it) }
        }.getOrNull().orEmpty()
        headers = restored
        return restored
    }
}
