package com.anilocal.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anilocal.app.domain.model.SkipMarker

/**
 * The AniLab-style skip pill: a light, fully-rounded button with dark text, shown only while the
 * playback position is inside an intro/outro window; tapping seeks past it.
 */
@Composable
fun SkipButton(
    positionMs: Long,
    markers: List<SkipMarker>,
    onSkip: (SkipMarker) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = markers.firstOrNull { positionMs in it.startMs until it.endMs }
    AnimatedVisibility(visible = active != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        if (active != null) {
            Button(
                onClick = { onSkip(active) },
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFECEDF1),
                    contentColor = Color(0xFF0E0F13),
                ),
                modifier = Modifier.height(40.dp),
            ) {
                Text(
                    if (active.type == SkipMarker.Type.INTRO) "Skip Intro" else "Skip Outro",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
