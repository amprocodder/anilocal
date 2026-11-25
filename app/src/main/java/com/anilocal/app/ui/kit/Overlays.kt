package com.anilocal.app.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.anilocal.app.ui.theme.AppTheme

/**
 * A modal dialog: a raised, 20dp-rounded panel with a [title], a body ([text], which scrolls when
 * it's long), and right-aligned actions — [dismissButton] then [confirmButton].
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val screen = LocalConfiguration.current
        Surface(
            modifier = modifier
                .padding(24.dp)
                .widthIn(max = 520.dp)
                .heightIn(max = (screen.screenHeightDp * 0.85f).dp),
            shape = RoundedCornerShape(20.dp),
            color = AppTheme.colors.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 14.dp)) {
                if (title != null) {
                    ProvideTextStyle(AppTheme.type.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp)) { title() }
                }
                if (text != null) {
                    Box(Modifier.padding(top = 10.dp).weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        CompositionLocalProvider(LocalContentColor provides AppTheme.colors.onSurfaceVariant) {
                            ProvideTextStyle(AppTheme.type.bodyMedium) { text() }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

/**
 * A menu that drops from the element it's placed in: below it (or above, when there's no room),
 * aligned to its start (or end, near the screen edge). Fades in; dismissed by a tap outside or Back.
 */
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val gap = with(LocalDensity.current) { 4.dp.roundToPx() }
    val position = remember(gap) { DropdownPosition(gap) }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        val alpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) { alpha.animateTo(1f, tween(120)) }
        Surface(
            modifier = modifier.graphicsLayer { this.alpha = alpha.value },
            shape = RoundedCornerShape(12.dp),
            color = AppTheme.colors.surfaceContainerHighest,
            border = BorderStroke(1.dp, AppTheme.colors.outlineVariant),
        ) {
            Column(
                Modifier
                    .width(IntrinsicSize.Max)
                    .widthIn(min = 160.dp)
                    .padding(vertical = 6.dp)
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
        }
    }
}

/** One menu row: optional [leadingIcon] slot (kept for alignment even when empty), then [text]. */
@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leadingIcon != null) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides AppTheme.colors.tertiary) { leadingIcon() }
            }
        }
        CompositionLocalProvider(LocalContentColor provides AppTheme.colors.onSurface) {
            ProvideTextStyle(AppTheme.type.bodyMedium) { text() }
        }
    }
}

private class DropdownPosition(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val startX = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right - popupContentSize.width
        val endX = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val x = when {
            startX >= 0 && startX + popupContentSize.width <= windowSize.width -> startX
            endX >= 0 && endX + popupContentSize.width <= windowSize.width -> endX
            else -> (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        }
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap - popupContentSize.height
        val y = when {
            below + popupContentSize.height <= windowSize.height -> below
            above >= 0 -> above
            else -> (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }
}
