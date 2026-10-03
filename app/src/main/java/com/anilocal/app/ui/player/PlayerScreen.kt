package com.anilocal.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    val playbackPlayer by vm.player.collectAsStateWithLifecycle()
    val recoveryState by vm.recoveryState.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()
    val activeMarker by vm.activeMarker.collectAsStateWithLifecycle()
    val subtitleScale by vm.subtitleScale.collectAsStateWithLifecycle()
    val subtitleBackground by vm.subtitleBackground.collectAsStateWithLifecycle()
    val captionStyle = remember(subtitleBackground) {
        CaptionStyleCompat(
            android.graphics.Color.WHITE,
            if (subtitleBackground) android.graphics.Color.argb(160, 0, 0, 0)
            else android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            android.graphics.Color.BLACK,
            null,
        )
    }

    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = playbackPlayer
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowSubtitleButton(true)
                }
            },
            update = { view ->
                if (view.player !== playbackPlayer) view.player = playbackPlayer
                // Apply subtitle preferences live (re-runs when scale/background change).
                view.subtitleView?.let { sv ->
                    sv.setApplyEmbeddedStyles(false)
                    sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitleScale)
                    sv.setStyle(captionStyle)
                }
            },
            onReset = null,
            onRelease = { view -> view.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        if (loading || recoveryState != PlaybackRecoveryState.Idle) {
            Column(
                modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.8f)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (recoveryState == PlaybackRecoveryState.Failed) {
                    Text("Playback couldn't restart. Please try again.", color = Color.White)
                    Button(onClick = vm::retryPlayback) { Text("Retry") }
                } else {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        when {
                            recoveryState is PlaybackRecoveryState.Restarting -> "Reconnecting…"
                            offline -> "Loading video…"
                            else -> "Checking sources…"
                        },
                        color = Color.White,
                    )
                }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
        }

        SkipButton(
            active = activeMarker,
            onSkip = vm::seekPast,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
        )
    }
}
