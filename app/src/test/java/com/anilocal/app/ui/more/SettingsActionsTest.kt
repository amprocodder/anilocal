package com.anilocal.app.ui.more

import com.anilocal.app.ui.performance.FakeMal
import com.anilocal.app.ui.performance.FakeSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsActionsTest {
    @Test fun burstOfChangesDuringSlowPersistenceWritesOnlyTheNewestQueuedValue() = runTest {
        val release = CompletableDeferred<Unit>()
        val values = mutableListOf<Int>()
        var active = 0
        var maximum = 0
        val setting = ConflatedSetting<Int>(backgroundScope) {
            values += it
            active++
            maximum = maxOf(maximum, active)
            try { if (it == 0) release.await() }
            finally { active-- }
        }
        setting.set(0)
        runCurrent()
        repeat(100) { setting.set(it + 1) }
        runCurrent()
        assertEquals(listOf(0), values)
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf(0, 100), values)
        assertEquals(1, maximum)
    }

    @Test fun repeatedQueuedValuesCoalesceWhileLaterWritesRemainAvailableToRestoreExternalChanges() = runTest {
        val values = mutableListOf<Float>()
        val setting = ConflatedSetting<Float>(backgroundScope) { values += it }
        setting.set(1.25f)
        runCurrent()
        repeat(100) { setting.set(1.25f) }
        runCurrent()
        assertEquals(listOf(1.25f, 1.25f), values)
    }

    @Test fun failedPreferenceWriteCanRetryTheSameValue() = runTest {
        var attempts = 0
        val setting = ConflatedSetting<Int>(backgroundScope) {
            attempts++
            if (attempts == 1) error("Storage temporarily unavailable")
        }
        setting.set(2)
        runCurrent()
        setting.set(2)
        runCurrent()
        assertEquals(2, attempts)
    }

    @Test fun scopeCancellationCancelsTheCurrentWriteAndDropsQueuedValues() = runTest {
        val scope = CoroutineScope(backgroundScope.coroutineContext + Job())
        val values = mutableListOf<Int>()
        var cancelled = false
        val setting = ConflatedSetting<Int>(scope) {
            values += it
            try { delay(1_000) }
            finally { cancelled = true }
        }
        setting.set(1)
        runCurrent()
        setting.set(2)
        scope.cancel()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(cancelled)
        assertEquals(listOf(1), values)
    }

    @Test fun manualSyncWaitsForUsernamePersistenceAndRejectsDuplicateRuns() = runTest {
        val saved = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        val settings = FakeSettings().apply { usernameWriteBlock = { saved.await() } }
        val usernames = mutableListOf<String>()
        val mal = FakeMal().apply { syncBlock = {
            usernames += settings.malUsername.value
            completed.await()
            Result.success(42)
        } }
        val actions = MalSyncActions(backgroundScope, settings, mal)
        repeat(10) { actions.sync("  new-user  ") }
        runCurrent()
        assertTrue(actions.syncing.value)
        assertEquals(listOf("new-user"), settings.usernameWrites)
        assertEquals(0, mal.syncCalls)
        saved.complete(Unit)
        runCurrent()
        repeat(10) { actions.sync("other-user") }
        assertEquals(1, mal.syncCalls)
        assertEquals(listOf("new-user"), usernames)
        completed.complete(Unit)
        runCurrent()
        assertFalse(actions.syncing.value)
        assertEquals("Synced 42 titles", actions.status.value)
    }

    @Test fun earlierSaveCannotOverwriteTheUsernameUsedBySync() = runTest {
        val release = CompletableDeferred<Unit>()
        val settings = FakeSettings().apply { usernameWriteBlock = { if (it == "old") release.await() } }
        var syncedUsername: String? = null
        val mal = FakeMal().apply { syncBlock = {
            syncedUsername = settings.malUsername.value
            Result.success(1)
        } }
        val actions = MalSyncActions(backgroundScope, settings, mal)
        actions.setUsername("old")
        runCurrent()
        actions.sync("new")
        runCurrent()
        assertEquals(0, mal.syncCalls)
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf("old", "new"), settings.usernameWrites)
        assertEquals("new", syncedUsername)
        assertEquals("new", settings.malUsername.value)
    }

    @Test fun syncFailuresReleaseTheGuardAndAllowRetry() = runTest {
        val settings = FakeSettings()
        val mal = FakeMal().apply { syncBlock = { throw IllegalStateException("Offline") } }
        val actions = MalSyncActions(backgroundScope, settings, mal)
        actions.sync("user")
        runCurrent()
        assertFalse(actions.syncing.value)
        assertEquals("Sync failed: Offline", actions.status.value)
        mal.syncBlock = { Result.success(8) }
        actions.sync()
        runCurrent()
        assertEquals("Synced 8 titles", actions.status.value)
        assertEquals(2, mal.syncCalls)
    }
}
