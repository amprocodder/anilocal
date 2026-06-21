package com.anilocal.app.data.metadata.mal

import com.anilocal.app.data.local.MalDao
import com.anilocal.app.data.local.MalEntryEntity
import com.anilocal.app.domain.model.MalListEntry
import com.anilocal.app.domain.model.MalStatus
import com.anilocal.app.domain.repo.MalRepository
import com.anilocal.app.domain.repo.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MalRepositoryImpl @Inject constructor(
    private val api: MalApi,
    private val dao: MalDao,
    private val settings: SettingsRepository,
) : MalRepository {

    override fun list(status: MalStatus): Flow<List<MalListEntry>> =
        dao.observeByStatus(status.api).map { rows -> rows.map { it.toEntry() } }

    override suspend fun sync(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val username = settings.malUsername.first().trim()
            require(username.isNotEmpty()) { "Enter your MAL username first" }

            val rows = mutableListOf<MalEntryEntity>()
            var offset = 0
            var page = 0
            while (page < MAX_PAGES) {                     // safety cap ≈ 15k entries
                page++
                val batch = api.animeList(username = username, offset = offset)
                if (batch.isEmpty()) break
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

            dao.clear()
            dao.upsertAll(rows)
            settings.setMalLastSynced(System.currentTimeMillis())
            rows.size
        }
    }

    override suspend fun syncIfDue() {
        if (!settings.malSyncEnabled.first()) return
        if (settings.malUsername.first().isBlank()) return
        if (System.currentTimeMillis() - settings.malLastSynced.first() < THROTTLE_MS) return
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
        const val MAX_PAGES = 50                          // ~300 entries/page

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
            url?.replace(Regex("/r/\\d+x\\d+/"), "/")?.substringBefore("?")
    }
}
