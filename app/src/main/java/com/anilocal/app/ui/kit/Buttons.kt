package com.anilocal.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anilocal.app.ui.theme.AppTheme

private val ButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
private val ButtonMinHeight = 44.dp

/**
 * The primary action: a filled pill in the accent, which darkens and dips 3% when pressed.
 * Disabled, it turns to the raised surface with muted text.
 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    containerColor: Color = AppTheme.colors.primary,
    contentColor: Color = AppTheme.colors.onPrimary,
    contentPadding: PaddingValues = ButtonPadding,
    content: @Composable RowScope.() -> Unit,
) {
    ButtonLayout(
        onClick, modifier, enabled, shape,
        container = if (enabled) containerColor else AppTheme.colors.surfaceContainerHighest,
        content = if (enabled) contentColor else AppTheme.colors.disabled,
        indication = PressIndication.Button,
        contentPadding = contentPadding,
        body = content,
    )
}

/** A secondary action: the same pill on the raised surface. */
@Composable
fun TonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    containerColor: Color = AppTheme.colors.surfaceContainerHighest,
    contentColor: Color = AppTheme.colors.onSurface,
    contentPadding: PaddingValues = ButtonPadding,
    content: @Composable RowScope.() -> Unit,
) {
    ButtonLayout(
        onClick, modifier, enabled, shape,
        container = containerColor,
        content = if (enabled) contentColor else AppTheme.colors.disabled,
        indication = PressIndication(pressedAlpha = 0.08f, pressedScale = 0.97f),
        contentPadding = contentPadding,
        body = content,
    )
}

/** A low-emphasis action: label only, in the bright accent ("View all", "Reload", dialog actions). */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = AppTheme.colors.tertiary,
    content: @Composable RowScope.() -> Unit,
) {
    ButtonLayout(
        onClick, modifier, enabled, CircleShape,
        container = Color.Transparent,
        content = if (enabled) contentColor else AppTheme.colors.disabled,
        indication = PressIndication.Default,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        body = content,
    )
}

@Composable
private fun ButtonLayout(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    container: Color,
    content: Color,
    indication: PressIndication,
    contentPadding: PaddingValues,
    body: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .defaultMinSize(minHeight = ButtonMinHeight)
            .clip(shape)
            // The indication wraps the background so the whole pill dips, not just its label.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = indication,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .background(container)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            ProvideTextStyle(AppTheme.type.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)) {
                body()
            }
        }
    }
}

/**
 * An icon-only button: a 44dp circular touch target (the caller's size wins) with a tint flash
 * on press. Disabled, its icon dims.
 */
@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconButtonLayout(onClick, modifier, enabled, Color.Transparent, content)
}

/** An icon button on a raised circle (Details' download and My List buttons). */
@Composable
fun TonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = AppTheme.colors.surfaceContainerHighest,
    content: @Composable () -> Unit,
) {
    IconButtonLayout(onClick, modifier, enabled, containerColor, content)
}

@Composable
private fun IconButtonLayout(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    container: Color,
    content: @Composable () -> Unit,
) {
    val tint = LocalContentColor.current
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) tint else tint.copy(alpha = 0.38f),
            content = content,
        )
    }
}
