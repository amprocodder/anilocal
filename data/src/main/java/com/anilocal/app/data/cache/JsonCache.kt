package com.anilocal.app.data.cache

import com.anilocal.app.data.local.CacheDao
import com.anilocal.app.data.local.CacheEntryEntity
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.lang.reflect.Type
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** A cached value plus how old it is — lets callers apply their own freshness policy. */
data class Aged<T>(val value: T, val ageMs: Long)

/**
 * Moshi-backed key → JSON cache over the disposable cache DB. This is what makes the app usable
 * without any server reachable: repositories route reads through [cached], which serves any cached
 * hit instantly (stale-while-revalidate — see its KDoc) and only blocks on the network when the
 * cache is cold. Key namespaces in use: `home:*`/`browse:*`/`search:*`/`popular:*`/`detail:*`
 * (AniList catalog), `skip:*` (AniSkip), `src:*` (source title match), `extrepo:*` (extension repo
 * index), `malmap:*` (MAL→AniList ids — kept forever, everything else is swept after [MAX_AGE_MS]).
 *
 * Every DB touch is failure-isolated (a cache hiccup must never take down the network path), and
 * a JSON row that no longer parses (model shape changed) just reads as a miss.
 */
@Singleton
class JsonCache @Inject constructor(
    private val dao: CacheDao,
    private val moshi: Moshi,
    @Named("appScope") private val appScope: CoroutineScope,
) {

    init {
        // One sweep per process: drop entries nothing has refreshed in MAX_AGE_MS. The id map is
        // exempt — it's a few bytes per row and is the only entry not re-derivable offline.
        appScope.launch {
            runCatching { dao.prune(System.currentTimeMillis() - MAX_AGE_MS, "malmap:%") }
        }
    }

    suspend fun <T : Any> getAged(key: String, type: Type): Aged<T>? = runCatching {
        val row = dao.get(key) ?: return@runCatching null
        val value = adapter<T>(type).fromJson(row.json) ?: return@runCatching null
        Aged(value, System.currentTimeMillis() - row.updatedAt)
    }.getOrNull()

    suspend fun <T : Any> getFresh(key: String, type: Type, maxAgeMs: Long): T? =
        getAged<T>(key, type)?.takeIf { it.ageMs <= maxAgeMs }?.value

    suspend fun <T : Any> put(key: String, type: Type, value: T) {
        runCatching { dao.put(CacheEntryEntity(key, adapter<T>(type).toJson(value), System.currentTimeMillis())) }
    }

    suspend fun remove(key: String) {
        runCatching { dao.delete(key) }
    }

    /** Every parseable value whose key starts with [keyPrefix] (e.g. "bestsrc:"). For enumerating a
     *  whole namespace — rows that don't parse to [type] are skipped. */
    suspend fun <T : Any> valuesUnder(keyPrefix: String, type: Type): List<T> = runCatching {
        dao.entriesLike("$keyPrefix%").mapNotNull { row -> adapter<T>(type).fromJson(row.json) }
    }.getOrDefault(emptyList())

    /**
     * The stale-while-revalidate read: ANY cached entry — fresh or stale — is served immediately
     * (a screen never blocks on the network once it has been seen; on a connected-but-dead network
     * the retry/timeout budget would otherwise keep it blank for ~45s). An entry older than
     * [ttlMs] additionally kicks a deduplicated background refresh that writes through for the
     * next read. Only a cold cache blocks on [fetch], and only then can its failure propagate.
     *
     * With [preferStaleOverEmpty], a fetch that "succeeds" with an empty collection is treated as
     * suspect and never written: it won't overwrite non-empty data (AniList degrades to
     * HTTP 200 + null data under load, which must not erase a good cached page), and on a cold
     * cache it's returned WITHOUT caching so the next visit refetches instead of pinning a
     * transient empty for the whole TTL.
     */
    suspend fun <T : Any> cached(
        key: String,
        type: Type,
        ttlMs: Long,
        preferStaleOverEmpty: Boolean = false,
        fetch: suspend () -> T,
    ): T {
        val aged = getAged<T>(key, type)
        if (aged != null) {
            if (aged.ageMs > ttlMs) refreshInBackground(key, type, preferStaleOverEmpty, fetch)
            return aged.value
        }
        val fresh = fetch()
        if (!(preferStaleOverEmpty && fresh.isEmptyValue())) put(key, type, fresh)
        return fresh
    }

    private fun <T : Any> refreshInBackground(
        key: String,
        type: Type,
        preferStaleOverEmpty: Boolean,
        fetch: suspend () -> T,
    ) {
        if (!inFlight.add(key)) return
        appScope.launch {
            try {
                val fresh = fetch()
                if (!(preferStaleOverEmpty && fresh.isEmptyValue())) put(key, type, fresh)
            } catch (_: Exception) {
                // Refresh is best-effort: the caller already got the stale value.
            } finally {
                inFlight.remove(key)
            }
        }
    }

    private val inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private fun Any.isEmptyValue() = (this as? Collection<*>)?.isEmpty() == true

    private fun <T> adapter(type: Type): JsonAdapter<T> {
        @Suppress("UNCHECKED_CAST")
        return moshi.adapter<Any>(type) as JsonAdapter<T>
    }

    private companion object {
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000   // sweep entries untouched for 30 days
    }
}
