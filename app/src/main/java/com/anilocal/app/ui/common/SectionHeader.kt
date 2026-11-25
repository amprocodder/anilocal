package com.anilocal.app.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import com.anilocal.app.ui.icons.AppIcons
import com.anilocal.app.ui.kit.Icon
import com.anilocal.app.ui.kit.Text
import com.anilocal.app.ui.kit.TextButton
import com.anilocal.app.ui.theme.AppTheme

/** Section header: the row's title and, when there's more, a "View all" link in the accent. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
    horizontalPadding: Dp = 16.dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
    ) {
        Text(
            text = title,
            style = AppTheme.type.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (onSeeAll != null) {
            // A real touch target (the label alone was ~20dp tall), with the accent kept for things you can press.
            TextButton(onClick = onSeeAll, modifier = Modifier.height(36.dp)) {
                Text("View all", fontSize = 13.sp)
                Icon(AppIcons.Filled.ChevronRight, null, Modifier.size(16.dp))
            }
        }
    }
}
