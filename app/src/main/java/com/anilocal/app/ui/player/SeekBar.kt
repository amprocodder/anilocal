package com.anilocal.app.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.anilocal.app.domain.model.SkipMarker

/**
 * A custom scrubber that draws the played/buffered progress and highlights the AniSkip INTRO/OUTRO
 * windows as colored ranges. Everything is held in millisecond space and converted to px only inside
 * the draw/gesture lambdas (using the live canvas width). While the user drags, a local [scrubMs]
 * preview wins over the polled [positionMs] so the thumb doesn't jitter against the poll loop.
 *
 * @param onScrub        called continuously while dragging (preview only — no seek); also signals the
 *                       caller that a scrub is in progress (e.g. to pause the controls auto-hide).
 * @param onScrubFinished called on tap or drag-end with the final target ms (the real seek).
 */
@Composable
fun SeekBar(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    markers: List<SkipMarker>,
    onScrub: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
    onScrubCancel: () -> Unit = {},
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFFE5256B),
) {
    val density = LocalDensity.current
    val trackH = with(density) { 4.dp.toPx() }
    val thumbR = with(density) { 7.dp.toPx() }
    val known = durationMs > 0L

    var scrubMs by remember { mutableStateOf<Long?>(null) }
    val shown = (scrubMs ?: positionMs).coerceIn(0L, if (known) durationMs else 0L)

    val cTrack = Color.White.copy(alpha = 0.25f)
    val cBuffer = Color.White.copy(alpha = 0.45f)
    val cIntro = Color(0xFF4CAF50).copy(alpha = 0.65f)
    val cOutro = Color(0xFFFF9800).copy(alpha = 0.65f)

    Box(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(durationMs) {
                if (!known) return@pointerInput
                detectTapGestures { o ->
                    onScrubFinished(((o.x / size.width).coerceIn(0f, 1f) * durationMs).toLong())
                }
            }
            .pointerInput(durationMs) {
                if (!known) return@pointerInput
                fun toMs(x: Float) = ((x / size.width).coerceIn(0f, 1f) * durationMs).toLong()
                detectDragGestures(
                    onDragStart = { o -> scrubMs = toMs(o.x); onScrub(scrubMs!!) },
                    onDrag = { ch, _ -> ch.consume(); scrubMs = toMs(ch.position.x); onScrub(scrubMs!!) },
                    onDragEnd = { scrubMs?.let(onScrubFinished); scrubMs = null },
                    onDragCancel = { scrubMs = null; onScrubCancel() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            val w = size.width
            val cy = size.height / 2f
            val top = cy - trackH / 2f
            fun fx(ms: Long) = if (known) (ms.toFloat() / durationMs) * w else 0f
            val corner = CornerRadius(trackH / 2f, trackH / 2f)

            // 1) full track
            drawRoundRect(cTrack, Offset(0f, top), Size(w, trackH), corner)
            if (!known) return@Canvas
            // 2) skip ranges (over the track, under the played fill so consumed portions look played)
            markers.forEach { m ->
                val x0 = fx(m.startMs).coerceIn(0f, w)
                val x1 = fx(m.endMs).coerceIn(0f, w)
                if (x1 > x0) {
                    drawRect(
                        if (m.type == SkipMarker.Type.INTRO) cIntro else cOutro,
                        Offset(x0, top),
                        Size(x1 - x0, trackH),
                    )
                }
            }
            // 3) buffered fill
            drawRoundRect(cBuffer, Offset(0f, top), Size(fx(bufferedMs).coerceIn(0f, w), trackH), corner)
            // 4) played fill
            val px = fx(shown).coerceIn(0f, w)
            drawRoundRect(accent, Offset(0f, top), Size(px, trackH), corner)
            // 5) thumb
            drawCircle(accent, thumbR, Offset(px, cy))
        }
    }
}
