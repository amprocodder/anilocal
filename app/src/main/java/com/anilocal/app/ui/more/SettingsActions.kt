package com.anilocal.app.ui.more

import com.anilocal.app.domain.repo.MalRepository
import com.anilocal.app.domain.repo.SettingsRepository
import com.anilocal.app.ui.common.loadOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Writes at most one value at a time and keeps only the latest queued preference. */
internal class ConflatedSetting<T>(scope: CoroutineScope, persist: suspend (T) -> Unit) {
    private val updates = Channel<T>(Channel.CONFLATED)

    init {
        scope.launch {
            for (value in updates) {
                // A waiting receiver can get one value directly while the channel buffers the
                // rest of that burst. Drain that buffer before starting the next disk write.
                var latest = value
                while (true) {
                    val queued = updates.tryReceive()
                    if (queued.isFailure) break
                    latest = queued.getOrThrow()
                }
                // Do not remember a last-written value: another screen may change this setting.
                loadOrNull { persist(latest) }
            }
        }
    }

    fun set(value: T) { updates.trySend(value) }
}

/** Username persistence is ordered before sync; repeated Sync taps share the current run. */
internal class MalSyncActions(
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val mal: MalRepository,
) {
    private val usernameWrites = Mutex()
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    fun setUsername(username: String) {
        scope.launch {
            usernameWrites.withLock { loadOrNull { settings.setMalUsername(username.trim()) } }
        }
    }

    fun sync(username: String? = null) {
        if (_syncing.value) return
        _syncing.value = true
        _status.value = "Syncing…"
        scope.launch {
            try {
                usernameWrites.withLock {
                    if (username != null) settings.setMalUsername(username.trim())
                    _status.value = mal.sync().fold(
                        { "Synced $it titles" },
                        { "Sync failed: ${it.message}" },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _status.value = "Sync failed: ${error.message}"
            } finally {
                _syncing.value = false
            }
        }
    }
}
