package com.anilocal.app.data.local

import com.anilocal.app.domain.model.AnimeSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomRepositoriesTest {
    @Test fun repeatedPausedSavesDoNotWriteOrMoveContinueWatchingToTheTop() = runBlocking {
        val dao = FakeProgressDao()
        val repository = RoomProgressRepository(dao)
        repository.save(anime, 2, 12_345, 100_000)
        val savedAt = dao.rows.getValue(anime.id).updatedAt
        repeat(10) { repository.save(anime, 2, 12_345, 100_000) }
        assertEquals(1, dao.upserts)
        assertEquals(1, dao.lookups)
        assertEquals(savedAt, dao.rows.getValue(anime.id).updatedAt)

        // The persistent check also deduplicates a newly constructed repository after restart.
        RoomProgressRepository(dao).save(anime, 2, 12_345, 100_000)
        assertEquals(1, dao.upserts)
        assertEquals(2, dao.lookups)
        assertEquals(savedAt, dao.rows.getValue(anime.id).updatedAt)
    }

    @Test fun exactSeeksEpisodeChangesAndDurationChangesAreAlwaysSaved() = runBlocking {
        val dao = FakeProgressDao()
        val repository = RoomProgressRepository(dao)
        repository.save(anime, 2, 12_345, 100_000)
        repository.save(anime, 2, 12_344, 100_000)
        assertEquals(12_344L, dao.rows.getValue(anime.id).positionMs)
        repository.save(anime, 2, 12_344, 110_000)
        repository.save(anime, 3, 12_344, 110_000)
        repository.save(anime.copy(title = "Updated title"), 3, 12_344, 110_000)
        assertEquals(5, dao.upserts)
    }

    @Test fun aSaveAfterRemovingTheSamePositionCreatesTheEntryAgain() = runBlocking {
        val dao = FakeProgressDao()
        val repository = RoomProgressRepository(dao)
        repository.save(anime, 2, 12_345, 100_000)
        repository.remove(anime.id)
        repository.save(anime, 2, 12_345, 100_000)
        assertEquals(2, dao.upserts)
        assertEquals(12_345L, dao.rows.getValue(anime.id).positionMs)
    }

    @Test fun overlappingSavesSerializeAndKeepTheNewestExactPosition() = runBlocking {
        val dao = FakeProgressDao()
        val release = CompletableDeferred<Unit>()
        dao.writeBlock = release
        val repository = RoomProgressRepository(dao)
        val first = async(start = CoroutineStart.UNDISPATCHED) { repository.save(anime, 2, 100, 100_000) }
        val last = async(start = CoroutineStart.UNDISPATCHED) { repository.save(anime, 2, 101, 100_000) }
        release.complete(Unit)
        first.await()
        last.await()
        assertEquals(101L, dao.rows.getValue(anime.id).positionMs)
        assertEquals(2, dao.upserts)
    }

    @Test fun unchangedLibraryAndBadgeQueriesEmitOnlyActualContentChanges() = runBlocking {
        val first = LibraryEntity(anime.id, anime.title, anime.posterUrl, anime.idMal)
        val dao = FakeLibraryDao(
            flowOf(emptyList(), emptyList(), listOf(first), listOf(first)),
            flowOf(0, 0, 1, 1, 0),
        )
        val repository = RoomLibraryRepository(dao)
        assertEquals(listOf(emptyList<AnimeSummary>(), listOf(anime)), repository.library.toList())
        assertEquals(listOf(false, true, false), repository.isSaved(anime.id).toList())
    }

    private class FakeProgressDao : ProgressDao {
        val rows = HashMap<String, WatchProgressEntity>()
        var upserts = 0
        var lookups = 0
        var writeBlock: CompletableDeferred<Unit>? = null
        override fun observeRecent(limit: Int): Flow<List<WatchProgressEntity>> = flowOf(rows.values.toList())
        override suspend fun getById(animeId: String): WatchProgressEntity? { lookups++; return rows[animeId] }
        override suspend fun upsert(entity: WatchProgressEntity) {
            writeBlock?.await()
            upserts++
            rows[entity.animeId] = entity
        }
        override suspend fun deleteById(animeId: String) { rows.remove(animeId) }
    }

    private class FakeLibraryDao(
        private val items: Flow<List<LibraryEntity>>,
        private val counts: Flow<Int>,
    ) : LibraryDao {
        override fun observeAll() = items
        override fun countById(id: String) = counts
        override suspend fun countByIdNow(id: String) = 0
        override suspend fun insert(entity: LibraryEntity) = Unit
        override suspend fun delete(entity: LibraryEntity) = Unit
        override suspend fun deleteById(id: String) = Unit
    }

    private val anime = AnimeSummary("anime", "Title", null, 1)
}
