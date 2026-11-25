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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anilocal.app.ui.kit.Icon
import com.anilocal.app.ui.kit.Text
import com.anilocal.app.ui.theme.AppTheme

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
            style = AppTheme.type.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = AppTheme.type.bodyMedium,
                color = AppTheme.colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * Centered empty state: an icon in a raised circle, a short title and a muted hint. Replaces the
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
    val scheme = AppTheme.colors
    Column(
        modifier.widthIn(max = 360.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(56.dp).background(scheme.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
        }
        Text(
            title,
            style = AppTheme.type.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            message,
            style = AppTheme.type.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) Box(Modifier.padding(top = 6.dp)) { action() }
    }
}
