package com.anilocal.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * A solid band behind the status bar, faded in once scrolled content would otherwise run under the
 * clock and icons (edge-to-edge screens draw their content behind the status bar).
 */
@Composable
fun StatusBarScrim(visible: Boolean, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "statusBarScrim")
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .graphicsLayer { this.alpha = alpha }
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f)),
    )
}

/** True once this list has scrolled past [thresholdPx] (or its first item entirely). */
fun LazyListState.scrolledPast(thresholdPx: Int): Boolean =
    firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > thresholdPx
