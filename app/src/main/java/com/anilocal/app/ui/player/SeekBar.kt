package com.anilocal.app.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anilocal.app.domain.model.SkipMarker
import com.anilocal.app.ui.kit.Text
import kotlin.math.roundToInt

/**
 * The player's scrubber: track, buffered range, the INTRO/OUTRO windows as green/amber segments,
 * the played fill and a white thumb. Thin at rest; while you drag, the track thickens, the thumb
 * swells with a halo, and a bubble above your finger shows the target time — naming the intro or
 * outro when you're inside one. Everything is held in millisecond space and converted to px only
 * inside the draw/gesture lambdas (using the live width). While dragging, a local [scrubMs] preview
 * wins over the polled [positionMs] so the thumb doesn't jitter against the poll loop.
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
    // No default: callers pass the theme accent (a hex default here would drift from Theme.kt).
    accent: Color,
) {
    val known = durationMs > 0L
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var widthPx by remember { mutableIntStateOf(0) }
    val shown = (scrubMs ?: positionMs).coerceIn(0L, if (known) durationMs else 0L)
    // 0 at rest → 1 while dragging: drives track thickness, thumb size and halo.
    val active by animateFloatAsState(if (scrubMs != null) 1f else 0f, tween(120), label = "scrub")

    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .onSizeChanged { widthPx = it.width }
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
        Canvas(Modifier.fillMaxWidth().height(44.dp)) {
            val w = size.width
            val cy = size.height / 2f
            val trackH = (3f + 3f * active).dp.toPx()
            val top = cy - trackH / 2f
            val corner = CornerRadius(trackH / 2f, trackH / 2f)
            fun fx(ms: Long) = if (known) (ms.toFloat() / durationMs) * w else 0f

            // 1) full track
            drawRoundRect(TrackColor, Offset(0f, top), Size(w, trackH), corner)
            if (!known) return@Canvas
            // 2) buffered range
            drawRoundRect(BufferColor, Offset(0f, top), Size(fx(bufferedMs).coerceIn(0f, w), trackH), corner)
            // 3) skip windows: their own segments (a 1dp gap at each end) over the buffer, under the
            //    played fill so a consumed window reads as played
            val gap = 1.dp.toPx()
            markers.forEach { m ->
                val x0 = fx(m.startMs).coerceIn(0f, w) + gap
                val x1 = fx(m.endMs).coerceIn(0f, w) - gap
                if (x1 > x0) {
                    drawRoundRect(
                        if (m.type == SkipMarker.Type.INTRO) IntroColor else OutroColor,
                        Offset(x0, top), Size(x1 - x0, trackH), corner,
                    )
                }
            }
            // 4) played fill
            val px = fx(shown).coerceIn(0f, w)
            drawRoundRect(accent, Offset(0f, top), Size(px, trackH), corner)
            // 5) thumb, with a soft halo while dragging
            if (active > 0f) drawCircle(Color.White.copy(alpha = 0.18f * active), (9f + 8f * active).dp.toPx(), Offset(px, cy))
            drawCircle(Color.White, (6f + 3f * active).dp.toPx(), Offset(px, cy))
        }

        // The scrub bubble: centred over the thumb (kept on screen), floating above the bar.
        val target = scrubMs
        if (target != null && known) {
            val inside = markers.firstOrNull { target in it.startMs until it.endMs }
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .layout { measurable, constraints ->
                        val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        val x = (widthPx * target.toFloat() / durationMs - p.width / 2f)
                            .roundToInt().coerceIn(0, (widthPx - p.width).coerceAtLeast(0))
                        layout(0, 0) { p.place(x, -p.height + 2.dp.roundToPx()) }
                    }
                    .background(BubbleColor, RoundedCornerShape(8.dp))
                    .border(1.dp, BubbleBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    formatClock(target),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = TabularStyle,
                )
                if (inside != null) {
                    Text(
                        if (inside.type == SkipMarker.Type.INTRO) "Intro" else "Outro",
                        color = if (inside.type == SkipMarker.Type.INTRO) IntroColor else OutroColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private val TrackColor = Color.White.copy(alpha = 0.22f)
private val BufferColor = Color.White.copy(alpha = 0.38f)
private val IntroColor = Color(0xFF46C28E)
private val OutroColor = Color(0xFFF2A33A)
private val BubbleColor = Color(0xEB0B0D11)
private val BubbleBorder = Color(0xFF262B36)
private val TabularStyle = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")

/** "m:ss" / "h:mm:ss", the player's clock. */
internal fun formatClock(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
