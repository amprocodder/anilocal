package com.anilocal.app.ui.more

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anilocal.app.BuildConfig
import com.anilocal.app.domain.model.DownloadQuality
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

@Composable
fun MoreScreen(vm: MoreViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
    val signInClient = remember(webClientId, context) {
        if (webClientId.isBlank()) null else {
            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            GoogleSignIn.getClient(context, options)
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        runCatching {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            account.idToken?.let(vm::signInWithGoogle)
        }.onFailure {
            Toast.makeText(context, "Sign-in failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Offscreen sections stop composing/collecting; typing and dragging invalidate one section.
    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "heading", contentType = "heading") {
            Text("More", style = MaterialTheme.typography.titleLarge)
        }
        item(key = "skip", contentType = "switch") {
            val enabled by vm.autoSkip.collectAsStateWithLifecycle()
            SettingSwitch("Auto-skip intro/outro", "Skip automatically when a marker is reached", enabled, vm::setAutoSkip)
        }
        item(key = "wifi", contentType = "switch") {
            val enabled by vm.wifiOnly.collectAsStateWithLifecycle()
            SettingSwitch("Download over WiFi only", "Pause downloads on mobile data; resume on WiFi", enabled, vm::setWifiOnly)
        }
        item(key = "quality", contentType = "section") { DownloadQualitySettings(vm) }
        item(key = "subtitles", contentType = "section") { SubtitleSettings(vm) }
        item(key = "mal", contentType = "section") { MalSyncSettings(vm) }
        item(key = "account", contentType = "section") {
            AccountSettings(vm) {
                val client = signInClient
                if (client == null) {
                    Toast.makeText(context, "Add your Firebase project (GOOGLE_WEB_CLIENT_ID) to enable sign-in.", Toast.LENGTH_LONG).show()
                } else {
                    launcher.launch(client.signInIntent)
                }
            }
        }
        item(key = "sources", contentType = "section") { StreamingSourceSettings(vm) }
    }
}

@Composable
private fun SettingSwitch(title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                description?.let { Hint(it) }
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
        HorizontalDivider()
    }
}

@Composable
private fun DownloadQualitySettings(vm: MoreViewModel) {
    val quality by vm.downloadQuality.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Default download quality", style = MaterialTheme.typography.bodyLarge)
        DownloadQuality.entries.forEach { option ->
            Row(Modifier.fillMaxWidth().clickable { vm.setDownloadQuality(option) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = quality == option, onClick = { vm.setDownloadQuality(option) })
                Text(option.label, modifier = Modifier.padding(start = 8.dp))
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun SubtitleSettings(vm: MoreViewModel) {
    val savedScale by vm.subtitleScale.collectAsStateWithLifecycle()
    val background by vm.subtitleBackground.collectAsStateWithLifecycle()
    var sliderScale by rememberSaveable(savedScale) { mutableFloatStateOf(savedScale) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Subtitles", style = MaterialTheme.typography.titleMedium)
        Text("Text size: ${(sliderScale * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = sliderScale,
            onValueChange = { sliderScale = it },
            onValueChangeFinished = { vm.setSubtitleScale(sliderScale) },
            valueRange = 0.6f..2.0f,
        )
        SettingSwitch("Subtitle background", null, background, vm::setSubtitleBackground)
    }
}

@Composable
private fun MalSyncSettings(vm: MoreViewModel) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            Text("MyAnimeList Sync", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, "Toggle")
        }
        if (expanded) {
            val username by vm.malUsername.collectAsStateWithLifecycle()
            val enabled by vm.malSyncEnabled.collectAsStateWithLifecycle()
            val status by vm.syncStatus.collectAsStateWithLifecycle()
            val syncing by vm.syncing.collectAsStateWithLifecycle()
            var field by rememberSaveable(username) { mutableStateOf(username) }
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                label = { Text("MAL username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Active sync (on app open)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = vm::setMalSyncEnabled)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.setMalUsername(field) }, enabled = !syncing) { Text("Save") }
                OutlinedButton(onClick = { vm.syncMalNow(field) }, enabled = !syncing) { Text("Sync now") }
            }
            status?.let { Hint(it) }
            Hint("Enter your MAL username to mirror your PUBLIC list (read-only) — no API key needed. Make sure your list privacy is Public on MAL, then filter it on the Library tab.")
        }
        HorizontalDivider()
    }
}

@Composable
private fun AccountSettings(vm: MoreViewModel, onSignIn: () -> Unit) {
    val user by vm.user.collectAsStateWithLifecycle()
    val signingIn by vm.signingIn.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Account", style = MaterialTheme.typography.titleMedium)
        if (user != null) {
            Text(user?.displayName ?: user?.email ?: "Signed in", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = vm::signOut) { Text("Sign out") }
        } else {
            Button(onClick = onSignIn, enabled = !signingIn) { Text("Sign in with Google") }
            Hint("Sign-in uses your Firebase project.")
        }
        HorizontalDivider()
    }
}

@Composable
private fun StreamingSourceSettings(vm: MoreViewModel) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val selected by vm.selectedSourceId.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Preferred streaming source", style = MaterialTheme.typography.titleMedium)
        Hint("Playback chooses the fastest available connection. Downloads use your selected source.")
        sources.forEach { source ->
            Row(Modifier.fillMaxWidth().clickable { vm.setSelectedSource(source.id) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == source.id, onClick = { vm.setSelectedSource(source.id) })
                Column(Modifier.padding(start = 8.dp)) {
                    Text(source.name, style = MaterialTheme.typography.bodyLarge)
                    Hint(if (source.isExternal) "Extension · ${source.lang}" else "Built-in")
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
