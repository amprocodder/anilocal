package com.anilocal.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun CatalogFeedback(state: CatalogLoadState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    if (state == CatalogLoadState.Ready) return
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state == CatalogLoadState.Loading) {
            Text("Loading anime…", modifier = Modifier.weight(1f))
            LinearProgressIndicator(modifier = Modifier.width(64.dp))
        } else {
            Text("Couldn't load anime. Check your connection and retry.", modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}
