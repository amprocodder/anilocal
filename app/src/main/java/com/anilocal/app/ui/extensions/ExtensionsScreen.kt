package com.anilocal.app.ui.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anilocal.app.domain.model.ExtensionEntry
import com.anilocal.app.domain.repo.ExtensionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val extensions: ExtensionRepository,
) : ViewModel() {

    private val _available = MutableStateFlow<List<ExtensionEntry>>(emptyList())
    val available: StateFlow<List<ExtensionEntry>> = _available
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    /** Package names currently active (installed or private) and the subset the app can uninstall. */
    private val _installed = MutableStateFlow<Set<String>>(emptySet())
    val installed: StateFlow<Set<String>> = _installed
    private val _privateInstalled = MutableStateFlow<Set<String>>(emptySet())
    val privateInstalled: StateFlow<Set<String>> = _privateInstalled

    /** Packages with an install/uninstall in flight (for a per-row spinner). */
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy

    init { load() }

    fun load() = viewModelScope.launch {
        _loading.value = true
        _status.value = null
        _available.value = runCatching { extensions.available() }
            .getOrElse { _status.value = "Couldn't load repos: ${it.message}"; emptyList() }
        refreshInstalled()
        _loading.value = false
    }

    /** Silently install as a private extension — download → verify signing cert → load in place, no
     *  system-installer tap. Verification failures surface as a status line. */
    fun install(entry: ExtensionEntry) = viewModelScope.launch {
        _busy.value = _busy.value + entry.pkg
        _status.value = "Installing ${entry.name}…"
        val ok = runCatching { extensions.privateInstall(entry) }.getOrDefault(false)
        _status.value = if (ok) "Installed ${entry.name}"
        else "Couldn't install ${entry.name} — download or signature check failed"
        refreshInstalled()
        _busy.value = _busy.value - entry.pkg
    }

    fun uninstall(entry: ExtensionEntry) = viewModelScope.launch {
        _busy.value = _busy.value + entry.pkg
        runCatching { extensions.privateUninstall(entry.pkg) }
        _status.value = "Removed ${entry.name}"
        refreshInstalled()
        _busy.value = _busy.value - entry.pkg
    }

    private suspend fun refreshInstalled() {
        _installed.value = runCatching { extensions.installedPackages() }.getOrDefault(emptySet())
        _privateInstalled.value = runCatching { extensions.privatelyInstalled() }.getOrDefault(emptySet())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(onBack: () -> Unit, vm: ExtensionsViewModel = hiltViewModel()) {
    val available by vm.available.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val installed by vm.installed.collectAsStateWithLifecycle()
    val privateInstalled by vm.privateInstalled.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extensions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = { TextButton(onClick = vm::load) { Text("Reload") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Browse and install third-party extension sources — unaffiliated community APKs. " +
                    "They install silently in-app (signing cert verified against the repo) and no " +
                    "longer need a system-installer tap. AniList still provides browsing; the selected " +
                    "source only resolves streams. Pick one — or “Auto (best source)” — in More → Streaming source.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (loading && available.isEmpty()) {
                Text("Loading…", modifier = Modifier.padding(16.dp))
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(available, key = { it.pkg }) { entry ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${entry.lang} · v${entry.versionName}" + if (entry.isNsfw) " · 18+" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (entry.sourceNames.isNotEmpty()) {
                                    Text(
                                        entry.sourceNames.joinToString(", "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            ExtensionAction(
                                busy = entry.pkg in busy,
                                installed = entry.pkg in installed,
                                removable = entry.pkg in privateInstalled,
                                onInstall = { vm.install(entry) },
                                onUninstall = { vm.uninstall(entry) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionAction(
    busy: Boolean,
    installed: Boolean,
    removable: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    when {
        busy -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        // Privately installed → the app can remove it in place.
        removable -> OutlinedButton(onClick = onUninstall, shape = RoundedCornerShape(100.dp)) {
            Text("Uninstall")
        }
        // System-installed (sideloaded by the user) → present, but not ours to uninstall.
        installed -> Text(
            "Installed",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Button(onClick = onInstall, shape = RoundedCornerShape(100.dp)) { Text("Install") }
    }
}
