package com.anilocal.app.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anilocal.app.ui.theme.AppTheme
import kotlin.math.roundToInt

/**
 * A filter pill. Unselected: a hairline outline and muted label. Selected: filled in the deep
 * accent with a bright label — no checkmark, so a row of chips never shifts width as you pick.
 */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = AppTheme.colors
    Box(
        modifier
            .height(32.dp)
            .clip(CircleShape)
            .border(1.dp, if (selected) c.primaryContainer else c.outlineVariant, CircleShape)
            .background(if (selected) c.primaryContainer else Color.Transparent)
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (selected) c.onPrimaryContainer else c.onSurfaceVariant) {
            ProvideTextStyle(
                AppTheme.type.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                content = label,
            )
        }
    }
}

/** An on/off switch: accent track with a white knob when on, a dark track and grey knob when off. */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = AppTheme.colors
    val position by animateFloatAsState(if (checked) 1f else 0f, tween(160), label = "switch")
    val track by animateColorAsState(if (checked) c.primary else c.track, tween(160), label = "track")
    val knob by animateColorAsState(if (checked) Color.White else c.onSurfaceVariant, tween(160), label = "knob")
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    } else {
        Modifier
    }
    Box(modifier.size(width = 52.dp, height = 44.dp).then(toggle), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(width = 46.dp, height = 28.dp).alpha(if (enabled) 1f else 0.38f)) {
            drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2f))
            val radius = (10.dp + 1.dp * position).toPx()
            val inset = (4.dp - 1.dp * position).toPx()
            val travel = size.width - 2 * (inset + radius)
            drawCircle(knob, radius, Offset(inset + radius + travel * position, size.height / 2f))
        }
    }
}

/** A radio dot: a muted ring, or a bright ring around a dot when selected. */
@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = AppTheme.colors
    val dot by animateFloatAsState(if (selected) 1f else 0f, tween(140), label = "radio")
    val select = if (onClick != null) {
        Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    } else {
        Modifier
    }
    Box(modifier.size(44.dp).then(select), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(20.dp).alpha(if (enabled) 1f else 0.38f)) {
            val stroke = 2.dp.toPx()
            drawCircle(
                if (selected) c.tertiary else c.outline,
                radius = size.minDimension / 2f - stroke / 2f,
                style = Stroke(stroke),
            )
            if (dot > 0f) drawCircle(c.tertiary, radius = 5.dp.toPx() * dot)
        }
    }
}

/** A checkbox: a rounded outline, or an accent square with a white tick when checked. */
@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = AppTheme.colors
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
    } else {
        Modifier
    }
    Box(modifier.size(44.dp).then(toggle), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(20.dp).alpha(if (enabled) 1f else 0.38f)) {
            val corner = CornerRadius(5.dp.toPx())
            if (checked) {
                drawRoundRect(c.primary, cornerRadius = corner)
                val w = size.width
                val tick = Path().apply {
                    moveTo(w * 0.24f, w * 0.52f)
                    lineTo(w * 0.43f, w * 0.70f)
                    lineTo(w * 0.77f, w * 0.32f)
                }
                drawPath(tick, Color.White, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                val stroke = 2.dp.toPx()
                drawRoundRect(
                    c.outline,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = corner,
                    style = Stroke(stroke),
                )
            }
        }
    }
}

/**
 * A continuous slider: one 4dp track, accent fill, white thumb. With [steps] it snaps to
 * `steps + 1` equal intervals and ticks the haptics at each one — but draws no tick marks.
 */
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val c = AppTheme.colors
    val haptics = LocalHapticFeedback.current
    val latestValue by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
    val onFinished by rememberUpdatedState(onValueChangeFinished)
    val span = valueRange.endInclusive - valueRange.start
    fun snap(v: Float): Float {
        val clamped = v.coerceIn(valueRange.start, valueRange.endInclusive)
        if (steps <= 0 || span <= 0f) return clamped
        val intervals = steps + 1
        val i = ((clamped - valueRange.start) / span * intervals).roundToInt()
        return valueRange.start + span * i / intervals
    }
    fun emit(v: Float) {
        val snapped = snap(v)
        if (snapped != latestValue) {
            if (steps > 0) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            onChange(snapped)
        }
    }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val thumbRadius = 9.dp
    fun valueAt(x: Float, padPx: Float): Float {
        val usable = (widthPx - 2 * padPx).coerceAtLeast(1f)
        return valueRange.start + ((x - padPx) / usable).coerceIn(0f, 1f) * span
    }
    val fraction = if (span > 0f) ((value - valueRange.start) / span).coerceIn(0f, 1f) else 0f
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, steps)
                if (enabled) {
                    setProgress { target ->
                        emit(target)
                        onFinished?.invoke()
                        true
                    }
                }
            }
            .then(
                if (!enabled) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(valueRange, steps) {
                            val pad = thumbRadius.toPx()
                            detectTapGestures { o ->
                                emit(valueAt(o.x, pad))
                                onFinished?.invoke()
                            }
                        }
                        .pointerInput(valueRange, steps) {
                            val pad = thumbRadius.toPx()
                            detectHorizontalDragGestures(
                                onDragStart = { o -> emit(valueAt(o.x, pad)) },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    emit(valueAt(change.position.x, pad))
                                },
                                onDragEnd = { onFinished?.invoke() },
                                onDragCancel = { onFinished?.invoke() },
                            )
                        }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            widthPx = size.width
            val r = thumbRadius.toPx()
            val trackH = 4.dp.toPx()
            val y = size.height / 2f
            val start = r
            val end = size.width - r
            val x = start + (end - start) * fraction
            val corner = CornerRadius(trackH / 2f)
            drawRoundRect(c.track, Offset(start, y - trackH / 2f), Size(end - start, trackH), corner)
            drawRoundRect(c.primary, Offset(start, y - trackH / 2f), Size(x - start, trackH), corner)
            drawCircle(Color.White, r, Offset(x, y))
        }
    }
}
