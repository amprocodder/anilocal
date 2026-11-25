package com.anilocal.app.data.settings

import android.util.AtomicFile
import com.anilocal.app.data.remote.AppJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileNotFoundException

/**
 * The app's settings, as one flat JSON object (key → primitive) in [file], held in memory as a
 * [StateFlow][MutableStateFlow] that every settings Flow maps from. It's what DataStore did for this
 * app, in ~100 lines:
 * - read once, off the main thread, as soon as the store is created; readers wait for that load;
 * - edits are serialized, written to disk FIRST (atomically — [AtomicFile] writes a new file and
 *   renames it, keeping a backup until the rename lands, so a crash mid-write never loses settings),
 *   and only then published;
 * - an edit that changes nothing doesn't touch the disk.
 *
 * On first run after an update, [legacyDataStore] (DataStore's protobuf file) is read and carried
 * over; it's left in place, so a downgrade still finds its settings.
 */
internal class SettingsFile(
    file: File,
    private val legacyDataStore: File,
    scope: CoroutineScope,
) {
    private val atomic = AtomicFile(file)
    private val state = MutableStateFlow<Map<String, JsonPrimitive>?>(null)
    private val writes = Mutex()

    init {
        scope.launch(Dispatchers.IO) { state.value = load() }
    }

    /** Every stored value; emits after the initial load and after each edit. */
    val values: Flow<Map<String, JsonPrimitive>> = state.filterNotNull()

    suspend fun edit(transform: (MutableMap<String, JsonPrimitive>) -> Unit) {
        writes.withLock {
            val current = values.first()
            val next = current.toMutableMap().also(transform)
            if (next == current) return
            withContext(Dispatchers.IO) { write(next) }
            state.value = next
        }
    }

    private fun load(): Map<String, JsonPrimitive> {
        read()?.let { return it }
        // First run since the switch from DataStore: carry the user's settings over.
        val migrated = runCatching { LegacyDataStorePreferences.read(legacyDataStore) }.getOrDefault(emptyMap())
        if (migrated.isNotEmpty()) runCatching { write(migrated) }
        return migrated
    }

    private fun read(): Map<String, JsonPrimitive>? = try {
        val text = atomic.readFully().toString(Charsets.UTF_8)
        AppJson.parseToJsonElement(text).jsonObject.mapValues { it.value.jsonPrimitive }
    } catch (_: FileNotFoundException) {
        null
    } catch (_: Exception) {
        null   // unreadable: fall back to the legacy file / defaults rather than crash
    }

    private fun write(values: Map<String, JsonPrimitive>) {
        val out = atomic.startWrite()
        try {
            out.write(AppJson.encodeToString(JsonObject.serializer(), JsonObject(values)).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
    }
}
