package com.anilocal.app.data.metadata.mal

import com.anilocal.app.data.cache.SuspendingLruCache
import com.anilocal.app.data.local.MalDao
import com.anilocal.app.data.local.MalEntryEntity
import com.anilocal.app.domain.model.MalListEntry
import com.anilocal.app.domain.model.MalStatus
import com.anilocal.app.domain.repo.MalRepository
import com.anilocal.app.domain.repo.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MalRepositoryImpl internal constructor(
    private val api: MalApi,
    private val dao: MalDao,
    private val settings: SettingsRepository,
    private val nowMillis: () -> Long,
    private val dispatcher: CoroutineDispatcher,
) : MalRepository {
    @Inject constructor(api: MalApi, dao: MalDao, settings: SettingsRepository) :
        this(api, dao, settings, System::currentTimeMillis, Dispatchers.IO)

    // Startup, Library, and the manual Sync action can request the same list together.
    // A brief completed-result window also coalesces callers queued at request completion.
    private val syncs = SuspendingLruCache<String, Int>(
        maxEntries = 1, ttlMillis = 1_000, dispatcher = dispatcher,
    )

    override fun list(status: MalStatus): Flow<List<MalListEntry>> =
        dao.observeByStatus(status.api).distinctUntilChanged()
            .map { rows -> rows.map { it.toEntry() } }.flowOn(Dispatchers.Default)

    override fun all(): Flow<List<MalListEntry>> =
        dao.observeAll().distinctUntilChanged()
            .map { rows -> rows.map { it.toEntry() } }.flowOn(Dispatchers.Default)

    override suspend fun sync(): Result<Int> = withContext(dispatcher) {
        try {
            val username = settings.malUsername.first().trim()
            require(username.isNotEmpty()) { "Enter your MAL username first" }
            Result.success(syncs.getOrLoad(username) {
                val rows = mutableListOf<MalEntryEntity>()
                var offset = 0
                var reachedEnd = false
                for (page in 0 until MAX_PAGES) {
                    // Stop downloading an obsolete user's list after a settings change.
                    check(settings.malUsername.first().trim() == username) { "MAL username changed during sync" }
                    val batch = api.animeList(username = username, offset = offset)
                    if (batch.isEmpty()) {
                        reachedEnd = true
                        break
                    }
                    batch.forEach { e ->
                        val status = malStatus(e.status) ?: return@forEach
                        rows += MalEntryEntity(
                            malId = e.animeId,
                            title = e.title ?: "Untitled",
                            posterUrl = normalizeImage(e.imagePath),
                            status = status.api,
                            score = e.score ?: 0,
                            episodesWatched = e.episodesWatched ?: 0,
                            totalEpisodes = e.numEpisodes?.takeIf { it > 0 },
                        )
                    }
                    offset += batch.size
                }
                check(reachedEnd) { "MAL list exceeded the sync page limit; existing list retained" }
                check(settings.malUsername.first().trim() == username) { "MAL username changed during sync" }
                dao.replaceAll(rows)
                settings.setMalLastSynced(nowMillis())
                rows.size
            })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            lastFailedSyncAt = nowMillis()
            Result.failure(failure)
        }
    }

    @Volatile private var lastFailedSyncAt = 0L

    override suspend fun syncIfDue() {
        if (!settings.malSyncEnabled.first()) return
        if (settings.malUsername.first().isBlank()) return
        val lastSynced = settings.malLastSynced.first()
        val elapsed = nowMillis() - lastSynced
        if (lastSynced > 0 && elapsed >= 0 && elapsed < THROTTLE_MS) return
        val failedAgo = nowMillis() - lastFailedSyncAt
        if (lastFailedSyncAt > 0 && failedAgo >= 0 && failedAgo < FAIL_THROTTLE_MS) return
        sync()
    }

    private fun MalEntryEntity.toEntry() = MalListEntry(
        malId = malId,
        title = title,
        posterUrl = posterUrl,
        status = MalStatus.fromApi(status) ?: MalStatus.PLAN_TO_WATCH,
        score = score,
        episodesWatched = episodesWatched,
        totalEpisodes = totalEpisodes,
    )

    private companion object {
        const val THROTTLE_MS = 30 * 60 * 1000L          // re-sync at most every 30 min on open
        const val FAIL_THROTTLE_MS = 10 * 60 * 1000L
        const val MAX_PAGES = 50                          // ~300 entries/page
        val RESIZED_IMAGE = Regex("/r/\\d+x\\d+/")

        /** MAL's numeric list codes from load.json → our [MalStatus]. (5 is unused by MAL.) */
        fun malStatus(code: Int): MalStatus? = when (code) {
            1 -> MalStatus.WATCHING
            2 -> MalStatus.COMPLETED
            3 -> MalStatus.ON_HOLD
            4 -> MalStatus.DROPPED
            6 -> MalStatus.PLAN_TO_WATCH
            else -> null
        }

        /**
         * load.json gives a resized thumbnail like `.../r/192x272/images/anime/1/2.jpg?s=hash`.
         * Strip the `/r/WxH/` segment and the `?s=` query to get the full-size poster.
         */
        fun normalizeImage(url: String?): String? =
            url?.replace(RESIZED_IMAGE, "/")?.substringBefore("?")
    }
}
