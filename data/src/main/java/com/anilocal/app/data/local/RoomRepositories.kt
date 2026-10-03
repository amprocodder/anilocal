package com.anilocal.app.data.local

import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.ContinueWatching
import com.anilocal.app.domain.repo.LibraryRepository
import com.anilocal.app.domain.repo.ProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomLibraryRepository @Inject constructor(
    private val dao: LibraryDao,
) : LibraryRepository {

    override val library: Flow<List<AnimeSummary>> =
        dao.observeAll().distinctUntilChanged()
            .map { list -> list.map { AnimeSummary(it.id, it.title, it.posterUrl, it.idMal) } }
            .flowOn(Dispatchers.Default)

    override suspend fun toggle(item: AnimeSummary) {
        dao.toggle(LibraryEntity(item.id, item.title, item.posterUrl, item.idMal))
    }

    override fun isSaved(id: String): Flow<Boolean> =
        dao.countById(id).map { it > 0 }.distinctUntilChanged()
}

@Singleton
class RoomProgressRepository @Inject constructor(
    private val dao: ProgressDao,
) : ProgressRepository {

    private val writes = Mutex()
    private val lastSaved = LinkedHashMap<String, WatchProgressEntity>()

    override val continueWatching: Flow<List<ContinueWatching>> =
        dao.observeRecent(20).distinctUntilChanged().map { list ->
            list.map {
                ContinueWatching(
                    anime = AnimeSummary(it.animeId, it.title, it.posterUrl, it.idMal),
                    episodeNumber = it.episodeNumber,
                    positionMs = it.positionMs,
                    durationMs = it.durationMs,
                    updatedAt = it.updatedAt,
                )
            }
        }.flowOn(Dispatchers.Default)

    override suspend fun save(
        anime: AnimeSummary,
        episodeNumber: Int,
        positionMs: Long,
        durationMs: Long,
    ) {
        writes.withLock {
            val candidate = WatchProgressEntity(
                animeId = anime.id,
                title = anime.title,
                posterUrl = anime.posterUrl,
                idMal = anime.idMal,
                episodeNumber = episodeNumber,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = 0L,
            )
            if (lastSaved[anime.id]?.sameProgressAs(candidate) == true) return@withLock
            val entity = candidate.copy(updatedAt = System.currentTimeMillis())
            dao.upsertIfChanged(entity)
            lastSaved[anime.id] = entity
            // Bound this process-local shortcut; the database still deduplicates older entries.
            if (lastSaved.size > 64) lastSaved.remove(lastSaved.keys.first())
        }
    }

    override suspend fun remove(animeId: String) = writes.withLock {
        dao.deleteById(animeId)
        lastSaved.remove(animeId)
        Unit
    }
}
