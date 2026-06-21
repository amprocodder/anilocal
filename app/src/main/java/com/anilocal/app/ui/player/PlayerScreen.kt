package com.anilocal.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(onBack: () -> Unit, vm: PlayerViewModel = hiltViewModel()) {
    val position by vm.position.collectAsStateWithLifecycle()
    val markers by vm.markers.collectAsStateWithLifecycle()
    val subtitleScale by vm.subtitleScale.collectAsStateWithLifecycle()
    val subtitleBackground by vm.subtitleBackground.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = vm.player
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                }
            },
            update = { view ->
                // Apply subtitle preferences live (re-runs when scale/background change).
                view.subtitleView?.let { sv ->
                    sv.setApplyEmbeddedStyles(false)
                    sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitleScale)
                    val bg = if (subtitleBackground) android.graphics.Color.argb(160, 0, 0, 0)
                    else android.graphics.Color.TRANSPARENT
                    sv.setStyle(
                        CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            bg,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null,
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
        }

        SkipButton(
            positionMs = position,
            markers = markers,
            onSkip = vm::seekPast,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
        )
    }
}
