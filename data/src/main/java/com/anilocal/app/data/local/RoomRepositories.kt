package com.anilocal.app.data.local

import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.ContinueWatching
import com.anilocal.app.domain.repo.LibraryRepository
import com.anilocal.app.domain.repo.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomLibraryRepository @Inject constructor(
    private val dao: LibraryDao,
) : LibraryRepository {

    override val library: Flow<List<AnimeSummary>> =
        dao.observeAll().map { list -> list.map { AnimeSummary(it.id, it.title, it.posterUrl, it.idMal) } }

    override suspend fun toggle(item: AnimeSummary) {
        if (dao.countByIdNow(item.id) > 0) dao.deleteById(item.id)
        else dao.insert(LibraryEntity(item.id, item.title, item.posterUrl, item.idMal))
    }

    override fun isSaved(id: String): Flow<Boolean> = dao.countById(id).map { it > 0 }
}

@Singleton
class RoomProgressRepository @Inject constructor(
    private val dao: ProgressDao,
) : ProgressRepository {

    override val continueWatching: Flow<List<ContinueWatching>> =
        dao.observeRecent(20).map { list ->
            list.map {
                ContinueWatching(
                    anime = AnimeSummary(it.animeId, it.title, it.posterUrl, it.idMal),
                    episodeNumber = it.episodeNumber,
                    positionMs = it.positionMs,
                    durationMs = it.durationMs,
                    updatedAt = it.updatedAt,
                )
            }
        }

    override suspend fun save(
        anime: AnimeSummary,
        episodeNumber: Int,
        positionMs: Long,
        durationMs: Long,
    ) {
        dao.upsert(
            WatchProgressEntity(
                animeId = anime.id,
                title = anime.title,
                posterUrl = anime.posterUrl,
                idMal = anime.idMal,
                episodeNumber = episodeNumber,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun remove(animeId: String) = dao.deleteById(animeId)
}
