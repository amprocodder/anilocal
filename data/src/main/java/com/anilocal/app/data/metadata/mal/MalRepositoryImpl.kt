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
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class MalRepositoryImpl @Inject constructor(
    private val api: MalApi,
    private val dao: MalDao,
    private val settings: SettingsRepository,
    @Named("mal_client_id") private val defaultClientId: String,   // optional baked-in fallback
) : MalRepository {

    /** In-app Client ID wins; falls back to any baked-in build default (usually blank). */
    private suspend fun clientId(): String =
        settings.malClientId.first().trim().ifBlank { defaultClientId }

    override fun list(status: MalStatus): Flow<List<MalListEntry>> =
        dao.observeByStatus(status.api).map { rows -> rows.map { it.toEntry() } }

    override suspend fun sync(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val cid = clientId()
            require(cid.isNotBlank()) { "Enter your MAL Client ID first" }
            val username = settings.malUsername.first().trim()
            require(username.isNotEmpty()) { "Enter your MAL username first" }

            val rows = mutableListOf<MalEntryEntity>()
            var offset = 0
            var page = 0
            while (page < 10) {                           // safety cap ≈ 10k entries
                page++
                val resp = api.animeList(username = username, clientId = cid, offset = offset)
                val nodes = resp.data.orEmpty()
                nodes.forEach { n ->
                    val node = n.node ?: return@forEach
                    val status = n.listStatus?.status ?: return@forEach
                    rows += MalEntryEntity(
                        malId = node.id,
                        title = node.title ?: "Untitled",
                        posterUrl = node.mainPicture?.large ?: node.mainPicture?.medium,
                        status = status,
                        score = n.listStatus.score ?: 0,
                        episodesWatched = n.listStatus.episodesWatched ?: 0,
                        totalEpisodes = node.numEpisodes,
                    )
                }
                offset += nodes.size
                if (resp.paging?.next == null || nodes.isEmpty()) break
            }

            dao.clear()
            dao.upsertAll(rows)
            settings.setMalLastSynced(System.currentTimeMillis())
            rows.size
        }
    }

    override suspend fun syncIfDue() {
        if (!settings.malSyncEnabled.first()) return
        if (clientId().isBlank()) return
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
    }
}
