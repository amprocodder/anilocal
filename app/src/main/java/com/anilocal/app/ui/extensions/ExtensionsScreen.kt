package com.anilocal.app.ui.extensions

import com.anilocal.app.ui.common.SearchField
import androidx.compose.runtime.remember
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.anilocal.app.domain.repo.SettingsRepository
import com.anilocal.app.domain.source.SourceRegistry
import kotlinx.coroutines.flow.combine
import com.anilocal.app.ui.common.loadOrNull
import com.anilocal.app.ui.common.catalogResult
import com.anilocal.app.ui.common.CATALOG_LOAD_TIMEOUT_MS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Repo root from user input, or null if it isn't an http(s) URL. Strips index/meta file names. */
internal fun normalizeRepoUrl(input: String): String? {
    val url = input.trim().trimEnd('/')
        .removeSuffix("/index.min.json").removeSuffix("/index.json").removeSuffix("/repo.json")
        .trimEnd('/')
    val ok = (url.startsWith("https://") || url.startsWith("http://")) &&
        url.substringAfter("://").contains('.') && url.none { it.isWhitespace() }
    return url.takeIf { ok }
}

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val extensions: ExtensionRepository,
    private val settings: SettingsRepository,
    registry: SourceRegistry,
) : ViewModel() {

    /** User-configured repo base URLs. The app ships with none; the user adds them here. */
    val repos: StateFlow<List<String>> =
        settings.extensionRepoBaseUrls.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    /**
     * Privately installed extensions that no configured repo lists (e.g. the user removed the repo
     * they came from). Shown separately so they can still be uninstalled in-app; names come from the
     * loaded source when available.
     */
    val orphans: StateFlow<List<ExtensionEntry>> =
        combine(_available, _privateInstalled, registry.sources, _loading) { available, private, sources, loading ->
            // Mid-load the list is empty, which would flash every install as an orphan.
            if (loading) return@combine emptyList()
            val listed = available.mapTo(HashSet()) { it.pkg }
            private.filter { it !in listed }.sorted().map { pkg ->
                val source = sources.firstOrNull { it.info.pkg == pkg }?.info
                ExtensionEntry(
                    name = source?.name ?: pkg.substringAfterLast('.'),
                    pkg = pkg,
                    apkUrl = "",
                    lang = source?.lang.orEmpty(),
                    versionName = "",
                    isNsfw = false,
                    sourceNames = emptyList(),
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Packages with an install/uninstall in flight (for a per-row spinner). */
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy

    init {
        // (Re)load the extension list whenever the repo list changes — including the first value.
        viewModelScope.launch { settings.extensionRepoBaseUrls.distinctUntilChanged().collect { load(force = true) } }
    }

    /** Add a repo by URL; accepts the repo root or a pasted `index.min.json`/`repo.json` link. */
    fun addRepo(input: String): Boolean {
        val url = normalizeRepoUrl(input) ?: run {
            _status.value = "Enter a full http(s) repo URL"
            return false
        }
        viewModelScope.launch {
            val current = settings.extensionRepoBaseUrls.first()
            if (current.any { it.trimEnd('/') == url }) {
                _status.value = "That repo is already added"
            } else {
                settings.setExtensionRepoBaseUrls(current + url)
                _status.value = "Added repo"
            }
        }
        return true
    }

    fun removeRepo(url: String) {
        viewModelScope.launch {
            settings.setExtensionRepoBaseUrls(settings.extensionRepoBaseUrls.first().filterNot { it == url })
            _status.value = "Removed repo (installed extensions stay until you uninstall them)"
        }
    }

    fun load() {
        _status.value = null
        load(force = false)
    }

    private var loadJob: Job? = null

    private fun load(force: Boolean) {
        if (_loading.value && !force) return
        loadJob?.cancel()
        _loading.value = true
        loadJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                if (settings.extensionRepoBaseUrls.first().isEmpty()) {
                    _available.value = emptyList()
                    refreshInstalled()
                    return@launch
                }
                retryCatalog { extensions.available() }.fold(
                    { _available.value = it.distinctBy(ExtensionEntry::pkg) },
                    { _status.value = "Couldn't load repos: ${it.message}" },
                )
                refreshInstalled()
            } finally {
                // A superseded (cancelled) load must not clear the flag under the newer one.
                if (loadJob === coroutineContext[Job]) _loading.value = false
            }
        }
        loadJob?.start()
    }

    fun install(entry: ExtensionEntry) {
        if (entry.pkg in _busy.value) return
        _busy.value = _busy.value + entry.pkg
        _status.value = "Installing ${entry.name}…"
        viewModelScope.launch {
            try {
                when (val result = installWithRetry(entry)) {
                    true -> _status.value = "Installed ${entry.name}"
                    false -> _status.value =
                        "Couldn't install ${entry.name} — download or signature check failed"
                    null -> _status.value =
                        "Couldn't install ${entry.name} after automatic retries. Try again later."
                }
                refreshInstalled()
            } finally {
                _busy.value = _busy.value - entry.pkg
            }
        }
    }

    fun uninstall(entry: ExtensionEntry) {
        if (entry.pkg in _busy.value) return
        _busy.value = _busy.value + entry.pkg
        viewModelScope.launch {
            try {
                when (uninstallWithRetry(entry.pkg)) {
                    true -> _status.value = "Removed ${entry.name}"
                    false -> _status.value = "Couldn't remove ${entry.name}"
                    null -> _status.value =
                        "Couldn't remove ${entry.name} after automatic retries. Try again later."
                }
                refreshInstalled()
            } finally {
                _busy.value = _busy.value - entry.pkg
            }
        }
    }

    private suspend fun refreshInstalled() = coroutineScope {
        val installed = async { loadOrNull { extensions.installedPackages() } }
        val privateInstalled = async { loadOrNull { extensions.privatelyInstalled() } }
        installed.await()?.let { _installed.value = it }
        privateInstalled.await()?.let { _privateInstalled.value = it }
    }

    /** Retry only thrown transient failures; a false install result is a real verification failure. */
    private suspend fun installWithRetry(entry: ExtensionEntry): Boolean? {
        repeat(ACTION_RETRY_ATTEMPTS + 1) { attempt ->
            try {
                return extensions.privateInstall(entry)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (attempt < ACTION_RETRY_ATTEMPTS) {
                    delay(ACTION_RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }
        return null
    }

    /** Returns null only when every attempt threw; successful no-op uninstalls remain true. */
    private suspend fun uninstallWithRetry(pkg: String): Boolean? {
        repeat(ACTION_RETRY_ATTEMPTS + 1) { attempt ->
            try {
                extensions.privateUninstall(pkg)
                return true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (attempt < ACTION_RETRY_ATTEMPTS) {
                    delay(ACTION_RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }
        return null
    }

    /** Retry repository discovery briefly so a transient index/network outage needs no user tap. */
    private suspend fun <T> retryCatalog(block: suspend () -> T): Result<T> {
        var attempt = 0
        while (true) {
            val result = catalogResult(CATALOG_LOAD_TIMEOUT_MS, block)
            if (result.isSuccess || attempt >= AUTO_RETRY_ATTEMPTS) return result
            attempt++
            delay(AUTO_RETRY_DELAY_MS * attempt)
        }
    }

    private companion object {
        const val AUTO_RETRY_ATTEMPTS = 2
        const val AUTO_RETRY_DELAY_MS = 1_000L
        const val ACTION_RETRY_ATTEMPTS = 2
        const val ACTION_RETRY_DELAY_MS = 750L
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
    val repos by vm.repos.collectAsStateWithLifecycle()
    val orphans by vm.orphans.collectAsStateWithLifecycle()
    var repoInput by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf<String?>(null) }
    val languages = remember(available) { available.map { it.lang }.distinct().sortedWith(languageOrder) }
    val shown = remember(available, search, language) {
        val q = search.trim()
        available.filter { e ->
            (language == null || e.lang == language) &&
                (q.isEmpty() || e.name.contains(q, ignoreCase = true) ||
                    e.sourceNames.any { it.contains(q, ignoreCase = true) })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extensions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = { TextButton(onClick = vm::load, enabled = !loading) { Text("Reload") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        // Cap the column on tablets: 2900px-wide rows with the button at the far edge are hard to scan.
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxHeight().widthIn(max = 840.dp).fillMaxWidth()) {
            Text(
                "Add extension repos and install third-party sources — unaffiliated community APKs, " +
                    "installed in-app (signing cert verified against the repo when it publishes one). " +
                    "AniList still provides browsing; the selected source only resolves streams. " +
                    "Pick one — or “Auto (best source)” — in Settings → Streaming source.",
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
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "repos", contentType = "repos") {
                    RepoSection(
                        repos = repos,
                        input = repoInput,
                        onInputChange = { repoInput = it },
                        onAdd = { if (vm.addRepo(repoInput)) repoInput = "" },
                        onRemove = vm::removeRepo,
                    )
                }
                if (orphans.isNotEmpty()) {
                    item(key = "orphans-header", contentType = "header") {
                        Text(
                            "Installed — not in your repos",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(orphans, key = { "orphan-" + it.pkg }, contentType = { "extension" }) { entry ->
                        ExtensionRow(entry, busy = entry.pkg in busy, installed = true, removable = true,
                            onInstall = {}, onUninstall = { vm.uninstall(entry) })
                    }
                }
                if (repos.isEmpty()) {
                    item(key = "no-repos", contentType = "hint") {
                        Text(
                            "No repos added yet. AniLocal ships with no sources — add an extension " +
                                "repo URL above, or use “Install recommended sources” in Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                } else if (loading && available.isEmpty()) {
                    item(key = "loading", contentType = "hint") {
                        Text("Loading…", modifier = Modifier.padding(vertical = 16.dp))
                    }
                }
                if (available.isNotEmpty()) {
                    item(key = "available-header", contentType = "header") {
                        Text("Available", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    item(key = "filter", contentType = "filter") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SearchField(value = search, onValueChange = { search = it }, placeholder = "Search extensions…")
                            if (languages.size > 1) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item {
                                        FilterChip(selected = language == null, onClick = { language = null }, label = { Text("All") })
                                    }
                                    items(languages, key = { it }) { lang ->
                                        FilterChip(
                                            selected = language == lang,
                                            onClick = { language = if (language == lang) null else lang },
                                            label = { Text(languageLabel(lang)) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (shown.isEmpty()) {
                        item(key = "no-match", contentType = "hint") {
                            Text(
                                "No extensions match.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
                items(shown, key = { it.pkg }, contentType = { "extension" }) { entry ->
                    ExtensionRow(
                        entry,
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

/** "all" (multi-language) first, then by display name. */
private val languageOrder = compareBy<String>({ it != "all" }, { languageLabel(it) })

/** Human name for a repo language code ("en", "pt-BR", "all"), falling back to the code itself. */
private fun languageLabel(code: String): String {
    if (code == "all") return "Multi"
    val name = java.util.Locale.forLanguageTag(code).getDisplayName(java.util.Locale.ENGLISH)
    return name.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) } ?: code
}

@Composable
private fun ExtensionRow(
    entry: ExtensionEntry,
    busy: Boolean,
    installed: Boolean,
    removable: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
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
                val meta = listOfNotNull(
                    entry.lang.ifBlank { null },
                    entry.versionName.ifBlank { null }?.let { "v$it" },
                    "18+".takeIf { entry.isNsfw },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                busy = busy,
                installed = installed,
                removable = removable,
                onInstall = onInstall,
                onUninstall = onUninstall,
            )
        }
    }
}

@Composable
private fun RepoSection(
    repos: List<String>,
    input: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Repositories", style = MaterialTheme.typography.titleSmall)
            repos.forEach { url ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        url,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemove(url) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove repo")
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    label = { Text("Repo URL") },
                    placeholder = { Text("https://…/index.min.json") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAdd() }),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onAdd, enabled = input.isNotBlank(), shape = RoundedCornerShape(100.dp)) {
                    Text("Add")
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
