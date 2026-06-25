package com.anilocal.app.ui.source

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anilocal.app.domain.source.AnimeSource
import com.anilocal.app.domain.source.SourcePreference
import com.anilocal.app.domain.source.SourceRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Per-source settings screen for a [SourcePreference]-exposing extension (ConfigurableAnimeSource).
 * Reads the preferences fresh from the source, persists each edit back via [AnimeSource.setPreference]
 * (which writes the source's own SharedPreferences, picked up on its next request), then re-reads so
 * the screen reflects the new value. Degrades to "no settings" if the source has none or can't be read.
 */
@HiltViewModel
class SourcePreferencesViewModel @Inject constructor(
    savedState: SavedStateHandle,
    registry: SourceRegistry,
) : ViewModel() {

    private val sourceId: String = checkNotNull(savedState["sourceId"])
    private val source: AnimeSource? = registry.get(sourceId)
    val title: String = source?.info?.name ?: "Source settings"

    private val _prefs = MutableStateFlow<List<SourcePreference>>(emptyList())
    val prefs: StateFlow<List<SourcePreference>> = _prefs
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    init { reload() }

    private fun reload() = viewModelScope.launch {
        _loading.value = true
        _prefs.value = source?.let { runCatching { it.preferences() }.getOrDefault(emptyList()) } ?: emptyList()
        _loading.value = false
    }

    /** [value] is Boolean (Toggle), String (EditText/Select) or Set<String> (MultiSelect). */
    fun set(key: String, value: Any?) = viewModelScope.launch {
        source?.let { runCatching { it.setPreference(key, value) } }
        reload()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcePreferencesScreen(onBack: () -> Unit, vm: SourcePreferencesViewModel = hiltViewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    // The pref currently being edited in a dialog (EditText/Select/MultiSelect); Toggle is inline.
    var editing by remember { mutableStateOf<SourcePreference?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        when {
            loading && prefs.isEmpty() ->
                Text("Loading…", Modifier.padding(padding).padding(16.dp))
            prefs.isEmpty() ->
                Text(
                    "This source has no settings.",
                    Modifier.padding(padding).padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(prefs, key = { it.key }) { pref ->
                    PreferenceRow(
                        pref = pref,
                        onToggle = { vm.set(pref.key, it) },
                        onClick = { editing = pref },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    when (val p = editing) {
        is SourcePreference.EditText ->
            EditTextDialog(p, onConfirm = { vm.set(p.key, it); editing = null }, onDismiss = { editing = null })
        is SourcePreference.Select ->
            SelectDialog(p, onConfirm = { vm.set(p.key, it); editing = null }, onDismiss = { editing = null })
        is SourcePreference.MultiSelect ->
            MultiSelectDialog(p, onConfirm = { vm.set(p.key, it); editing = null }, onDismiss = { editing = null })
        else -> Unit // Toggle (inline) or nothing being edited
    }
}

@Composable
private fun PreferenceRow(pref: SourcePreference, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    when (pref) {
        is SourcePreference.Toggle -> Row(
            Modifier.fillMaxWidth().clickable { onToggle(!pref.value) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TitleAndSummary(pref.title, pref.summary, Modifier.weight(1f))
            Switch(checked = pref.value, onCheckedChange = onToggle)
        }
        else -> Column(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        ) {
            TitleAndSummary(pref.title, pref.currentSubtitle())
        }
    }
}

@Composable
private fun TitleAndSummary(title: String, summary: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (!summary.isNullOrBlank()) {
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The subtitle shown under a clickable (non-toggle) preference: its current value, else its summary. */
private fun SourcePreference.currentSubtitle(): String? = when (this) {
    is SourcePreference.EditText -> value.ifBlank { summary }
    is SourcePreference.Select -> labelFor(value, entries, entryValues) ?: summary
    is SourcePreference.MultiSelect ->
        values.mapNotNull { labelFor(it, entries, entryValues) }.takeIf { it.isNotEmpty() }
            ?.joinToString() ?: summary
    is SourcePreference.Toggle -> summary
}

/** The human label for [value] using parallel [entries]/[entryValues], falling back to the raw value. */
private fun labelFor(value: String, entries: List<String>, entryValues: List<String>): String? {
    val idx = entryValues.indexOf(value)
    return when {
        idx in entries.indices -> entries[idx]
        value.isNotBlank() -> value
        else -> null
    }
}

@Composable
private fun EditTextDialog(pref: SourcePreference.EditText, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(pref.value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pref.title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SelectDialog(pref: SourcePreference.Select, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pref.title) },
        text = {
            Column {
                pref.entries.forEachIndexed { i, label ->
                    val entryValue = pref.entryValues.getOrNull(i) ?: return@forEachIndexed
                    Row(
                        Modifier.fillMaxWidth().clickable { onConfirm(entryValue) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = entryValue == pref.value, onClick = { onConfirm(entryValue) })
                        Text(label, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MultiSelectDialog(
    pref: SourcePreference.MultiSelect,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember { pref.values.toMutableStateList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pref.title) },
        text = {
            Column {
                pref.entries.forEachIndexed { i, label ->
                    val entryValue = pref.entryValues.getOrNull(i) ?: return@forEachIndexed
                    val checked = entryValue in selected
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (checked) selected.remove(entryValue) else selected.add(entryValue)
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                if (it) selected.add(entryValue) else selected.remove(entryValue)
                            },
                        )
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected.toSet()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
