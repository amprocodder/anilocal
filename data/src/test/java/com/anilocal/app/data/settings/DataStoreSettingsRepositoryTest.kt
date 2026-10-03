package com.anilocal.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.anilocal.app.domain.model.DownloadQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataStoreSettingsRepositoryTest {
    @Test fun unrelatedPreferenceChangesDoNotWakeDownloadRequirementsOrPlaybackSettings() = runBlocking {
        val snapshots = listOf(
            emptyPreferences(),
            preferencesOf(subtitleScale to 1.2f),
            preferencesOf(subtitleScale to 1.2f, wifiOnly to false),
            preferencesOf(subtitleScale to 1.2f, wifiOnly to false, username to "viewer"),
        )
        val settings = DataStoreSettingsRepository(SnapshotStore(snapshots))
        assertEquals(listOf(true, false), settings.wifiOnlyDownloads.toList())
        assertEquals(listOf(1f, 1.2f), settings.subtitleScale.toList())
        assertEquals(listOf(true), settings.autoSkip.toList())
        assertEquals(listOf("", "viewer"), settings.malUsername.toList())
    }

    @Test fun multipleUnknownDownloadQualityValuesEmitOneFallback() = runBlocking {
        val settings = DataStoreSettingsRepository(SnapshotStore(listOf(
            emptyPreferences(),
            preferencesOf(quality to "old-removed-value"),
            preferencesOf(quality to "other-invalid-value"),
            preferencesOf(quality to DownloadQuality.P720.name),
        )))
        assertEquals(listOf(DownloadQuality.AUTO, DownloadQuality.P720), settings.downloadQuality.toList())
    }

    @Test fun defaultAndRepeatedPreferenceAssignmentsDoNotPersistRedundantValues() = runBlocking {
        val store = MemoryStore()
        val settings = DataStoreSettingsRepository(store)
        settings.setWifiOnlyDownloads(true)
        settings.setSubtitleScale(1f)
        assertEquals(0, store.writes)
        assertNull(store.data.value[wifiOnly])
        settings.setWifiOnlyDownloads(false)
        repeat(10) { settings.setWifiOnlyDownloads(false) }
        assertEquals(1, store.writes)
        assertEquals(false, store.data.value[wifiOnly])
    }

    @Test fun changingTheMalUserResetsThePreviousUsersSyncTimestampAtomically() = runBlocking {
        val store = MemoryStore()
        val settings = DataStoreSettingsRepository(store)
        settings.setMalUsername(" viewer ")
        settings.setMalLastSynced(123L)
        settings.setMalUsername(" viewer ")
        assertEquals(2, store.writes)
        assertEquals("viewer", store.data.value[username])
        assertEquals(123L, store.data.value[lastSynced])
        settings.setMalUsername("different-viewer")
        assertEquals(3, store.writes)
        assertEquals(0L, store.data.value[lastSynced])
    }

    private class SnapshotStore(private val snapshots: List<Preferences>) : DataStore<Preferences> {
        override val data: Flow<Preferences> get() = snapshots.asFlow()
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw UnsupportedOperationException("Read-only snapshots")
    }

    private class MemoryStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        var writes = 0
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(data.value)
            if (next != data.value) { writes++; data.value = next }
            return next
        }
    }

    private val wifiOnly = booleanPreferencesKey("wifi_only_downloads")
    private val subtitleScale = floatPreferencesKey("subtitle_scale")
    private val username = stringPreferencesKey("mal_username")
    private val quality = stringPreferencesKey("download_quality")
    private val lastSynced = longPreferencesKey("mal_last_synced")
}
