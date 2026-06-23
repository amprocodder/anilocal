package com.anilocal.app.ui.more

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.remember
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
fun MoreScreen(onBrowseExtensions: () -> Unit, vm: MoreViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val user by vm.user.collectAsStateWithLifecycle()
    val autoSkip by vm.autoSkip.collectAsStateWithLifecycle()
    val wifiOnly by vm.wifiOnly.collectAsStateWithLifecycle()
    val downloadQuality by vm.downloadQuality.collectAsStateWithLifecycle()
    val subtitleScale by vm.subtitleScale.collectAsStateWithLifecycle()
    val subtitleBackground by vm.subtitleBackground.collectAsStateWithLifecycle()

    val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
    val signInClient = remember(webClientId) {
        if (webClientId.isBlank()) null else {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            GoogleSignIn.getClient(context, gso)
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

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("More", style = MaterialTheme.typography.titleLarge)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Auto-skip intro/outro", style = MaterialTheme.typography.bodyLarge)
                Text("Skip automatically when a marker is reached",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = autoSkip, onCheckedChange = vm::setAutoSkip)
        }
        HorizontalDivider()

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Download over WiFi only", style = MaterialTheme.typography.bodyLarge)
                Text("Pause downloads on mobile data; resume on WiFi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = wifiOnly, onCheckedChange = vm::setWifiOnly)
        }
        HorizontalDivider()

        Text("Default download quality", style = MaterialTheme.typography.bodyLarge)
        DownloadQuality.entries.forEach { q ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.setDownloadQuality(q) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = downloadQuality == q, onClick = { vm.setDownloadQuality(q) })
                Text(q.label, modifier = Modifier.padding(start = 8.dp))
            }
        }
        HorizontalDivider()

        Text("Subtitles", style = MaterialTheme.typography.titleMedium)
        Text("Text size: ${(subtitleScale * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        var sliderScale by remember(subtitleScale) { mutableStateOf(subtitleScale) }
        Slider(
            value = sliderScale,
            onValueChange = { sliderScale = it },
            onValueChangeFinished = { vm.setSubtitleScale(sliderScale) },
            valueRange = 0.6f..2.0f,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Subtitle background", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = subtitleBackground, onCheckedChange = vm::setSubtitleBackground)
        }
        HorizontalDivider()

        // MyAnimeList Sync — expandable subsection.
        var malExpanded by remember { mutableStateOf(false) }
        val malUsername by vm.malUsername.collectAsStateWithLifecycle()
        val malSyncEnabled by vm.malSyncEnabled.collectAsStateWithLifecycle()
        val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()
        Row(
            Modifier.fillMaxWidth().clickable { malExpanded = !malExpanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("MyAnimeList Sync", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(if (malExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, "Toggle")
        }
        if (malExpanded) {
            var usernameField by remember(malUsername) { mutableStateOf(malUsername) }
            OutlinedTextField(
                value = usernameField,
                onValueChange = { usernameField = it },
                label = { Text("MAL username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Active sync (on app open)", style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f))
                Switch(checked = malSyncEnabled, onCheckedChange = vm::setMalSyncEnabled)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    vm.setMalUsername(usernameField.trim())
                }) { Text("Save") }
                OutlinedButton(onClick = {
                    vm.setMalUsername(usernameField.trim()); vm.syncMalNow()
                }) { Text("Sync now") }
            }
            syncStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Enter your MAL username to mirror your PUBLIC list (read-only) — no API key " +
                "needed. Make sure your list privacy is Public on MAL, then filter it on the Library tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()

        Text("Account", style = MaterialTheme.typography.titleMedium)
        if (user != null) {
            Text(user?.displayName ?: user?.email ?: "Signed in",
                style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = vm::signOut) { Text("Sign out") }
        } else {
            Button(onClick = {
                val client = signInClient
                if (client == null) {
                    Toast.makeText(
                        context,
                        "Add your Firebase project (GOOGLE_WEB_CLIENT_ID) to enable sign-in.",
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    launcher.launch(client.signInIntent)
                }
            }) { Text("Sign in with Google") }
            Text("Sign-in uses YOUR Firebase project, so it actually works (unlike a re-signed mod).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()

        val sources by vm.sources.collectAsStateWithLifecycle()
        val selectedSourceId by vm.selectedSourceId.collectAsStateWithLifecycle()
        Text("Streaming source", style = MaterialTheme.typography.titleMedium)
        Text("Where video is resolved from. Browsing and metadata always come from AniList.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        sources.forEach { src ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.setSelectedSource(src.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selectedSourceId == src.id,
                    onClick = { vm.setSelectedSource(src.id) },
                )
                Column(Modifier.padding(start = 8.dp)) {
                    Text(src.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (src.isExternal) "Extension · ${src.lang}" else "Built-in",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        OutlinedButton(onClick = onBrowseExtensions, modifier = Modifier.padding(top = 4.dp)) {
            Text("Browse extensions")
        }
    }
}
