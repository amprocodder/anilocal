package com.anilocal.app.data.metadata.mal

import com.anilocal.app.data.local.MalDao
import com.anilocal.app.data.local.MalEntryEntity
import com.anilocal.app.domain.model.DownloadQuality
import com.anilocal.app.domain.repo.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MalRepositoryImplTest {
    @Test fun startupAndManualSyncSharePaginationAndPublishOneCompleteReplacement() = runTest {
        val release = CompletableDeferred<Unit>()
        val api = FakeApi { _, offset ->
            if (offset == 0) {
                release.await()
                listOf(entry(1), entry(2, status = 6))
            } else emptyList()
        }
        val settings = FakeSettings()
        val dao = FakeDao()
        val repository = MalRepositoryImpl(api, dao, settings, { NOW }, StandardTestDispatcher(testScheduler))
        val startup = async { repository.syncIfDue() }
        val manual = async { repository.sync() }
        runCurrent()
        assertEquals(listOf("user" to 0), api.requests)
        release.complete(Unit)
        startup.await()
        assertEquals(2, manual.await().getOrThrow())
        assertEquals(listOf("user" to 0, "user" to 2), api.requests)
        assertEquals(1, dao.replacements)
        assertEquals(0, dao.separateClears)
        assertEquals(listOf(1, 2), dao.rows.value.map { it.malId })
        assertEquals("https://cdn/images/anime/1.jpg", dao.rows.value.first().posterUrl)
        assertEquals(NOW, settings.malLastSynced.value)
        repository.syncIfDue()
        assertEquals(2, api.requests.size)
    }

    @Test fun failingLaterPageRetainsPreviousLibraryAndCanRetryImmediately() = runTest {
        var fail = true
        val api = FakeApi { _, offset ->
            when {
                offset == 0 -> listOf(entry(2))
                fail -> throw IOException("Connection lost")
                else -> emptyList()
            }
        }
        val dao = FakeDao(listOf(saved(1)))
        val settings = FakeSettings()
        val repository = MalRepositoryImpl(api, dao, settings, { NOW }, StandardTestDispatcher(testScheduler))
        assertTrue(repository.sync().isFailure)
        assertEquals(listOf(1), dao.rows.value.map { it.malId })
        assertEquals(0L, settings.malLastSynced.value)
        fail = false
        assertEquals(1, repository.sync().getOrThrow())
        assertEquals(listOf(2), dao.rows.value.map { it.malId })
        assertEquals(4, api.requests.size)
    }

    @Test fun cancellationStopsApiAndNeverCommitsAnIncompleteSync() = runTest {
        val stopped = CompletableDeferred<Unit>()
        val api = FakeApi { _, _ ->
            try { awaitCancellation() } finally { stopped.complete(Unit) }
        }
        val dao = FakeDao(listOf(saved(1)))
        val settings = FakeSettings()
        val repository = MalRepositoryImpl(api, dao, settings, { NOW }, StandardTestDispatcher(testScheduler))
        val request = async { repository.sync() }
        runCurrent()
        request.cancelAndJoin()
        runCurrent()
        assertTrue(stopped.isCompleted)
        assertEquals(0, dao.replacements)
        assertEquals(0L, settings.malLastSynced.value)
    }

    @Test fun usernameChangedDuringDownloadDoesNotOverwriteLibraryWithOldUser() = runTest {
        val release = CompletableDeferred<Unit>()
        val api = FakeApi { _, _ -> release.await(); listOf(entry(2)) }
        val settings = FakeSettings()
        val dao = FakeDao(listOf(saved(1)))
        val repository = MalRepositoryImpl(api, dao, settings, { NOW }, StandardTestDispatcher(testScheduler))
        val request = async { repository.sync() }
        runCurrent()
        settings.setMalUsername("different-user")
        release.complete(Unit)
        assertTrue(request.await().isFailure)
        assertEquals(1, api.requests.size)
        assertEquals(listOf(1), dao.rows.value.map { it.malId })
        assertEquals(0, dao.replacements)
    }

    @Test fun automaticSyncHonorsDisabledBlankAndRecentSettingsWhileManualSyncRemainsAvailable() = runTest {
        val api = FakeApi { _, _ -> emptyList() }
        val settings = FakeSettings()
        val repository = MalRepositoryImpl(api, FakeDao(), settings, { NOW }, StandardTestDispatcher(testScheduler))
        settings.setMalSyncEnabled(false)
        repository.syncIfDue()
        settings.setMalSyncEnabled(true)
        settings.setMalUsername("  ")
        repository.syncIfDue()
        settings.setMalUsername("user")
        settings.setMalLastSynced(NOW - 100)
        repository.syncIfDue()
        assertTrue(api.requests.isEmpty())
        assertEquals(0, repository.sync().getOrThrow())
        assertEquals(listOf("user" to 0), api.requests)
    }

    private class FakeApi(private val respond: suspend (String, Int) -> List<MalLoadEntry>) : MalApi {
        val requests = mutableListOf<Pair<String, Int>>()
        override suspend fun animeList(username: String, offset: Int, status: Int): List<MalLoadEntry> {
            requests += username to offset
            return respond(username, offset)
        }
    }

    private class FakeDao(initial: List<MalEntryEntity> = emptyList()) : MalDao {
        val rows = MutableStateFlow(initial)
        var replacements = 0
        var separateClears = 0
        override fun observeAll() = rows
        override fun observeByStatus(status: String) = rows.map { list -> list.filter { it.status == status } }
        override suspend fun upsertAll(entries: List<MalEntryEntity>) { rows.value = entries }
        override suspend fun clear() { separateClears++; rows.value = emptyList() }
        override suspend fun replaceAll(entries: List<MalEntryEntity>) { replacements++; rows.value = entries }
    }

    private class FakeSettings : SettingsRepository {
        override val malUsername = MutableStateFlow("user")
        override val malSyncEnabled = MutableStateFlow(true)
        override val malLastSynced = MutableStateFlow(0L)
        override val autoSkip = flowOf(false)
        override val wifiOnlyDownloads = flowOf(false)
        override val downloadQuality = flowOf(DownloadQuality.P720)
        override val subtitleScale = flowOf(1f)
        override val subtitleBackground = flowOf(false)
        override val selectedSourceId = flowOf("sample")
        override suspend fun setMalUsername(username: String) { malUsername.value = username }
        override suspend fun setMalSyncEnabled(enabled: Boolean) { malSyncEnabled.value = enabled }
        override suspend fun setMalLastSynced(epochMs: Long) { malLastSynced.value = epochMs }
        override suspend fun setAutoSkip(enabled: Boolean) = Unit
        override suspend fun setWifiOnlyDownloads(enabled: Boolean) = Unit
        override suspend fun setDownloadQuality(quality: DownloadQuality) = Unit
        override suspend fun setSubtitleScale(scale: Float) = Unit
        override suspend fun setSubtitleBackground(enabled: Boolean) = Unit
        override suspend fun setSelectedSourceId(id: String) = Unit
    }

    private companion object {
        const val NOW = 1_000_000_000L
        fun entry(id: Int, status: Int = 1) = MalLoadEntry(
            id, "Anime $id", "https://cdn/r/192x272/images/anime/$id.jpg?s=hash", status, 8, 2, 12,
        )
        fun saved(id: Int) = MalEntryEntity(id, "Saved", null, "watching", 0, 0, 12)
    }
}
