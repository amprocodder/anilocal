package com.anilocal.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Large page title for the tab screens (Explore, My List, Downloads, Settings), with an optional
 * muted subtitle — one consistent header instead of each screen sizing its own.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    horizontalPadding: Dp = 16.dp,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = horizontalPadding), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Centered empty state: a glowing icon medallion, a short title and a muted hint. Replaces the
 * bare line of text that used to float in an empty screen. Still no spinner or dialog — it's
 * just a friendlier blank (see CLAUDE.md's silent-degrade convention).
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.widthIn(max = 360.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(88.dp)
                .background(
                    Brush.radialGradient(listOf(scheme.primary.copy(alpha = 0.45f), scheme.primaryContainer.copy(alpha = 0.15f))),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(38.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) Box(Modifier.padding(top = 6.dp)) { action() }
    }
}
