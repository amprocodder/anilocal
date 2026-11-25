package com.anilocal.app.ui.extensions

import com.anilocal.app.ui.icons.AppIcons
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import com.anilocal.app.ui.common.EmptyState
import com.anilocal.app.ui.common.SectionHeader
import com.anilocal.app.ui.common.SearchField
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anilocal.app.appViewModel
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import com.anilocal.app.ui.kit.TextInput
import com.anilocal.app.ui.kit.TopBar
import com.anilocal.app.ui.kit.Button
import com.anilocal.app.ui.kit.CircularProgressIndicator
import com.anilocal.app.ui.kit.FilterChip
import com.anilocal.app.ui.kit.Icon
import com.anilocal.app.ui.kit.IconButton
import com.anilocal.app.ui.kit.Surface
import com.anilocal.app.ui.kit.Text
import com.anilocal.app.ui.kit.TextButton
import com.anilocal.app.ui.kit.TonalButton
import com.anilocal.app.ui.theme.AppTheme

/** Repo root from user input, or null if it isn't an http(s) URL. Strips index/meta file names. */
internal fun normalizeRepoUrl(input: String): String? {
    val url = input.trim().trimEnd('/')
        .removeSuffix("/index.min.json").removeSuffix("/index.json").removeSuffix("/repo.json")
        .trimEnd('/')
    val ok = (url.startsWith("https://") || url.startsWith("http://")) &&
        url.substringAfter("://").contains('.') && url.none { it.isWhitespace() }
    return url.takeIf { ok }
}

class ExtensionsViewModel(
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

@Composable
fun ExtensionsScreen(onBack: () -> Unit, vm: ExtensionsViewModel = appViewModel { ExtensionsViewModel(it.extensions, it.settings, it.sources) }) {
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

    Column(Modifier.fillMaxSize()) {
        TopBar("Extensions", onBack = onBack) {
            TextButton(onClick = vm::load, enabled = !loading) { Text("Reload") }
        }
        // Cap the column on tablets: 2900px-wide rows with the button at the far edge are hard to scan.
        Box(Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxHeight().widthIn(max = 840.dp).fillMaxWidth()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AppTheme.colors.primaryContainer.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, AppTheme.colors.primary.copy(alpha = 0.3f)),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        AppIcons.Outlined.VerifiedUser, null,
                        tint = AppTheme.colors.tertiary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        "Add extension repos and install third-party sources — unaffiliated community APKs, " +
                            "installed in-app (signing cert verified against the repo when it publishes one). " +
                            "AniList still provides browsing; the selected source only resolves streams. " +
                            "Pick one — or “Auto (best source)” — in Settings → Streaming source.",
                        style = AppTheme.type.bodySmall,
                        color = AppTheme.colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            status?.let {
                Text(
                    it,
                    style = AppTheme.type.bodyMedium,
                    color = AppTheme.colors.primary,
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
                        SectionHeader("Installed — not in your repos", Modifier.padding(top = 12.dp), horizontalPadding = 0.dp)
                    }
                    items(orphans, key = { "orphan-" + it.pkg }, contentType = { "extension" }) { entry ->
                        ExtensionRow(entry, busy = entry.pkg in busy, installed = true, removable = true,
                            onInstall = {}, onUninstall = { vm.uninstall(entry) })
                    }
                }
                if (repos.isEmpty()) {
                    item(key = "no-repos", contentType = "hint") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                AppIcons.Outlined.Extension,
                                "No repos added yet",
                                "AniLocal ships with no sources. Add an extension repo URL above, " +
                                    "or use “Install recommended sources” in Settings.",
                                Modifier.padding(top = 24.dp),
                            )
                        }
                    }
                } else if (loading && available.isEmpty()) {
                    item(key = "loading", contentType = "hint") {
                        Text("Loading…", modifier = Modifier.padding(vertical = 16.dp))
                    }
                }
                if (available.isNotEmpty()) {
                    item(key = "available-header", contentType = "header") {
                        SectionHeader("Available", Modifier.padding(top = 12.dp), horizontalPadding = 0.dp)
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
                                style = AppTheme.type.bodyMedium,
                                color = AppTheme.colors.onSurfaceVariant,
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
        shape = RoundedCornerShape(16.dp),
        color = AppTheme.colors.surfaceContainer,
        border = BorderStroke(1.dp, AppTheme.colors.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Monogram badge: extensions ship no icon we can load, so lead with a tinted initial.
            Box(
                Modifier
                    .size(44.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(AppTheme.colors.primary, AppTheme.colors.primaryContainer),
                        ),
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    entry.name.removePrefix("Aniyomi: ").trim().take(1).uppercase(),
                    style = AppTheme.type.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(entry.name, style = AppTheme.type.bodyLarge, fontWeight = FontWeight.SemiBold)
                val meta = listOfNotNull(
                    entry.lang.ifBlank { null },
                    entry.versionName.ifBlank { null }?.let { "v$it" },
                    "18+".takeIf { entry.isNsfw },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = AppTheme.type.bodySmall,
                        color = AppTheme.colors.onSurfaceVariant,
                    )
                }
                if (entry.sourceNames.isNotEmpty()) {
                    Text(
                        entry.sourceNames.joinToString(", "),
                        style = AppTheme.type.labelSmall,
                        color = AppTheme.colors.onSurfaceVariant,
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
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = AppTheme.colors.surfaceContainer,
        border = BorderStroke(1.dp, AppTheme.colors.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .background(AppTheme.colors.primaryContainer, RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Outlined.Inventory2, null, tint = AppTheme.colors.tertiary, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Repositories", style = AppTheme.type.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (repos.isEmpty()) "Paste an index.min.json URL to add one"
                        else "${repos.size} repo${if (repos.size == 1) "" else "s"} added",
                        style = AppTheme.type.bodySmall,
                        color = AppTheme.colors.onSurfaceVariant,
                    )
                }
            }
            repos.forEach { url ->
                Surface(shape = RoundedCornerShape(12.dp), color = AppTheme.colors.surfaceContainerHigh) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            AppIcons.Outlined.Link, null,
                            tint = AppTheme.colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            url,
                            style = AppTheme.type.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 10.dp),
                        )
                        IconButton(onClick = { onRemove(url) }) {
                            Icon(AppIcons.Filled.Close, contentDescription = "Remove repo")
                        }
                    }
                }
            }
            // Bottom-aligned: the field's label sits above it, and Add lines up with the box itself.
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(
                    value = input,
                    onValueChange = onInputChange,
                    label = "Repo URL",
                    placeholder = "https://…/index.min.json",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAdd() }),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onAdd, enabled = input.isNotBlank()) {
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
        removable -> TonalButton(onClick = onUninstall) {
            Text("Uninstall")
        }
        // System-installed (sideloaded by the user) → present, but not ours to uninstall.
        installed -> Text(
            "Installed",
            style = AppTheme.type.labelLarge,
            color = AppTheme.colors.onSurfaceVariant,
        )
        else -> Button(onClick = onInstall) { Text("Install") }
    }
}
