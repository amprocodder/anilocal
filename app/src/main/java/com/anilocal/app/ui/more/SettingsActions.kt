package com.anilocal.app.ui.more

import com.anilocal.app.domain.repo.MalRepository
import com.anilocal.app.domain.repo.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
                // Preference writes are tiny but can race DataStore/extension startup. Retry a
                // transient failure while preserving cancellation so leaving the screen never
                // leaves a worker running against a dead ViewModel.
                var attempt = 0
                while (attempt < MAX_WRITE_ATTEMPTS) {
                    try {
                        persist(latest)
                        currentCoroutineContext().ensureActive()
                        break
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        attempt++
                        if (attempt < MAX_WRITE_ATTEMPTS) delay(WRITE_RETRY_DELAY_MS * attempt)
                    }
                }
            }
        }
    }

    fun set(value: T) { updates.trySend(value) }

    private companion object {
        const val MAX_WRITE_ATTEMPTS = 3
        const val WRITE_RETRY_DELAY_MS = 250L
    }
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
