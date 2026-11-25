package com.anilocal.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.anilocal.app.ui.theme.AppTheme

/**
 * A text field: a filled, rounded box with a hairline that turns bright while focused, an optional
 * [label] above it, and a [placeholder] inside while it's empty.
 */
@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val c = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(label, style = AppTheme.type.labelMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = AppTheme.type.bodyLarge.copy(color = c.onSurface),
            cursorBrush = SolidColor(c.tertiary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            decorationBox = { inner ->
                Row(
                    Modifier
                        .defaultMinSize(minHeight = 44.dp)
                        .background(c.surface, shape)
                        .border(if (focused) 1.5.dp else 1.dp, if (focused) c.tertiary else c.outlineVariant, shape)
                        .padding(start = if (leadingIcon != null) 12.dp else 14.dp, end = if (trailingIcon != null) 2.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (leadingIcon != null) {
                        CompositionLocalProvider(LocalContentColor provides if (focused) c.onSurface else c.onSurfaceVariant) {
                            leadingIcon()
                        }
                    }
                    Box(Modifier.weight(1f).padding(vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = AppTheme.type.bodyLarge, color = c.onSurfaceVariant, maxLines = 1)
                        }
                        inner()
                    }
                    if (trailingIcon != null) {
                        CompositionLocalProvider(LocalContentColor provides c.onSurfaceVariant) { trailingIcon() }
                    }
                }
            },
        )
    }
}
