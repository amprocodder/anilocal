package com.anilocal.app.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anilocal.app.ui.icons.AppIcons
import com.anilocal.app.ui.kit.Icon
import com.anilocal.app.ui.kit.IconButton
import com.anilocal.app.ui.kit.TextInput

/**
 * The app-wide search box: the shared text input with a search glyph, and a clear button once
 * there's text.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    TextInput(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        leadingIcon = { Icon(AppIcons.Filled.Search, null, Modifier.size(20.dp)) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(AppIcons.Filled.Close, "Clear search", Modifier.size(18.dp))
                }
            }
        } else {
            null
        },
        modifier = modifier.fillMaxWidth(),
    )
}
