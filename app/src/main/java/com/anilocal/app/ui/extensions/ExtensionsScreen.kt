package com.anilocal.app.ui.extensions

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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
import java.io.File
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

    init { load() }

    fun load() = viewModelScope.launch {
        _loading.value = true
        _status.value = null
        _available.value = runCatching { extensions.available() }
            .getOrElse { _status.value = "Couldn't load repos: ${it.message}"; emptyList() }
        _loading.value = false
    }

    /** Download the APK, then hand the file to [onReady] (the UI launches the system installer). */
    fun install(entry: ExtensionEntry, onReady: (File) -> Unit) = viewModelScope.launch {
        _status.value = "Downloading ${entry.name}…"
        runCatching { extensions.downloadApk(entry) }
            .onSuccess { _status.value = null; onReady(it) }
            .onFailure { _status.value = "Download failed: ${it.message}" }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(onBack: () -> Unit, vm: ExtensionsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val available by vm.available.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()

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
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Browse and install third-party extension sources — unaffiliated community APKs, " +
                    "installed via your system installer. AniList still provides browsing; the " +
                    "selected source only resolves streams. Pick an installed one in More → Streaming source.",
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
            LazyColumn(Modifier.fillMaxSize()) {
                items(available, key = { it.pkg }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${entry.lang} · v${entry.versionName}" + if (entry.isNsfw) " · 18+" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = {
                            vm.install(entry) { file ->
                                runCatching { launchInstall(context, file) }.onFailure {
                                    Toast.makeText(
                                        context,
                                        "Install failed: ${it.message}",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        }) { Text("Install") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** Hand the downloaded APK to the system package installer via a FileProvider content URI. */
private fun launchInstall(context: Context, apk: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
