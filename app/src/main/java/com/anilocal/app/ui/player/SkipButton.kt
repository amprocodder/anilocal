package com.anilocal.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.anilocal.app.domain.model.SkipMarker

/**
 * The FreakIntroButton equivalent: shown only while playback position is inside an
 * intro/outro window; tapping seeks past it.
 */
@Composable
fun SkipButton(
    active: SkipMarker?,
    onSkip: (SkipMarker) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = active != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        if (active != null) {
            Button(onClick = { onSkip(active) }) {
                Text(if (active.type == SkipMarker.Type.INTRO) "Skip Intro" else "Skip Outro")
            }
        }
    }
}
