package com.anilocal.app.ui.kit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anilocal.app.ui.theme.AppTheme

/** A determinate bar: a rounded fill over a track, as tall as its modifier makes it (3dp default). */
@Composable
fun LinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.tertiary,
    trackColor: Color = AppTheme.colors.track,
) {
    val p = progress().coerceIn(0f, 1f)
    Canvas(
        modifier
            .size(width = 240.dp, height = 3.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(p, 0f..1f) },
    ) {
        val corner = CornerRadius(size.height / 2f)
        drawRoundRect(trackColor, cornerRadius = corner)
        if (p > 0f) drawRoundRect(color, size = Size(size.width * p, size.height), cornerRadius = corner)
    }
}

/** An indeterminate bar: a short segment sweeping along the track. */
@Composable
fun LinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.tertiary,
    trackColor: Color = AppTheme.colors.track,
) {
    val sweep by rememberInfiniteTransition(label = "linear").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "sweep",
    )
    Canvas(modifier.size(width = 240.dp, height = 3.dp).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        val corner = CornerRadius(size.height / 2f)
        drawRoundRect(trackColor, cornerRadius = corner)
        val segment = size.width * 0.35f
        val start = (size.width + segment) * sweep - segment
        val left = start.coerceAtLeast(0f)
        val right = (start + segment).coerceAtMost(size.width)
        if (right > left) drawRoundRect(color, Offset(left, 0f), Size(right - left, size.height), corner)
    }
}

/** An indeterminate spinner: a rotating arc that grows and shrinks. */
@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.tertiary,
    strokeWidth: Dp = 4.dp,
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val rotation by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "rotation",
    )
    val arc by transition.animateFloat(
        30f, 270f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "arc",
    )
    Canvas(modifier.size(40.dp).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        drawArc(
            color = color,
            startAngle = rotation + (270f - arc) / 2f,
            sweepAngle = arc,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}
