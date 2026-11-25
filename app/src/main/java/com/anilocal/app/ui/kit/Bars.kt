package com.anilocal.app.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anilocal.app.ui.icons.AppIcons
import com.anilocal.app.ui.theme.AppTheme

/** A screen's top bar: Back (when [onBack] is set), a bold [title], and trailing [actions]. */
@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(AppTheme.colors.background)
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = if (onBack != null) 4.dp else 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(AppIcons.AutoMirrored.Filled.ArrowBack, "Back") }
        }
        Text(
            title,
            style = AppTheme.type.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 0.dp),
        )
        actions()
    }
}

/** The bottom navigation bar: 64dp of tabs over a hairline, above the system navigation bar. */
@Composable
fun NavigationBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val line = AppTheme.colors.outlineVariant
    Column(
        modifier
            .fillMaxWidth()
            .background(AppTheme.colors.background)
            .drawBehind { drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/**
 * One tab: its icon in a pill that fills with the deep accent when selected (fading in), over a
 * small label that brightens.
 */
@Composable
fun RowScope.NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    val c = AppTheme.colors
    val pill by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "pill")
    val iconTint by animateColorAsState(if (selected) c.onPrimaryContainer else c.onSurfaceVariant, tween(180), label = "icon")
    val labelColor by animateColorAsState(if (selected) c.onSurface else c.onSurfaceVariant, tween(180), label = "label")
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(width = 56.dp, height = 30.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(width = 56.dp, height = 30.dp)
                    .graphicsLayer {
                        alpha = pill
                        scaleX = 0.6f + 0.4f * pill
                    }
                    .background(c.primaryContainer, CircleShape),
            )
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Text(
            label,
            color = labelColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
