package com.anilocal.app.ui.player

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.BidiFormatter
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Caption text size as a fraction of the video frame's height at scale 1 — what the player used
 *  before it drew its own captions (Media3 SubtitleView's default). */
const val CAPTION_TEXT_SIZE_FRACTION = 0.0533f

/**
 * How captions look: white text with a black outline and, optionally, a translucent box behind each
 * line. [textSizePx] pins an absolute size (the Settings preview); otherwise text is
 * [CAPTION_TEXT_SIZE_FRACTION] × [scale] of the frame's height.
 */
data class CaptionStyle(
    val scale: Float = 1f,
    val background: Boolean = true,
    val textSizePx: Float? = null,
)

/**
 * Draws [cues] over the video frame this fills — placement follows each cue's line/position anchors,
 * defaulting to bottom-centre above an 8% margin. Embedded styling (fonts, colours, sizes) is ignored
 * so every source's captions look the same; bitmap cues (PGS/VobSub) are drawn where they say.
 */
@OptIn(UnstableApi::class)
@Composable
fun Captions(cues: List<Cue>, style: CaptionStyle, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val painter = remember(density) { CaptionPainter(density) }
    Spacer(
        modifier.drawBehind {
            if (cues.isEmpty()) return@drawBehind
            val textSize = style.textSizePx ?: (CAPTION_TEXT_SIZE_FRACTION * style.scale * size.height)
            if (textSize <= 0f) return@drawBehind
            drawIntoCanvas { canvas ->
                for (cue in cues) painter.draw(canvas.nativeCanvas, cue, textSize, style.background, size.width.toInt(), size.height.toInt())
            }
        },
    )
}

/**
 * The text/bitmap layout and drawing for one cue — a port of the parts of Media3's SubtitlePainter
 * this app used (outline edge, background box, no embedded styles), so captions look exactly as they
 * did when Media3's SubtitleView drew them.
 */
@OptIn(UnstableApi::class)
private class CaptionPainter(density: Float) {
    private val outlineWidth = (2f * density).roundToInt().toFloat()
    private val textPaint = TextPaint().apply { isAntiAlias = true; isSubpixelText = true }
    private val bitmapPaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }

    fun draw(canvas: Canvas, cue: Cue, textSizePx: Float, background: Boolean, width: Int, height: Int) {
        val bitmap = cue.bitmap
        if (bitmap != null) {
            drawBitmap(canvas, cue, bitmap, width, height)
        } else {
            drawText(canvas, cue, textSizePx, background, width, height)
        }
    }

    private fun drawText(canvas: Canvas, cue: Cue, textSizePx: Float, background: Boolean, parentWidth: Int, parentHeight: Int) {
        // Embedded styling is dropped: only the plain text is laid out.
        val plain = cue.text?.toString()?.let(::bidiWrap)
        if (TextUtils.isEmpty(plain)) return
        textPaint.textSize = textSizePx
        val textPaddingX = (textSizePx * INNER_PADDING_RATIO + 0.5f).toInt()
        var availableWidth = parentWidth - textPaddingX * 2
        if (cue.size != Cue.DIMEN_UNSET) availableWidth = (availableWidth * cue.size).toInt()
        if (availableWidth <= 0) return

        val alignment = cue.textAlignment ?: Layout.Alignment.ALIGN_CENTER
        val text = SpannableStringBuilder(plain)
        // With an outline edge the box goes on the edge layout, so it sits behind the outline.
        val edgeText = SpannableStringBuilder(plain).apply {
            if (background) setSpan(BackgroundColorSpan(BOX_COLOR), 0, length, Spanned.SPAN_PRIORITY)
        }
        var layout = staticLayout(text, availableWidth, alignment)
        val textHeight = layout.height
        var textWidth = 0
        for (i in 0 until layout.lineCount) textWidth = max(ceil(layout.getLineWidth(i)).toInt(), textWidth)
        if (cue.size != Cue.DIMEN_UNSET && textWidth < availableWidth) textWidth = availableWidth
        textWidth += textPaddingX * 2

        val textLeft: Int
        val textRight: Int
        if (cue.position != Cue.DIMEN_UNSET) {
            val anchor = (parentWidth * cue.position).roundToInt()
            val left = when (cue.positionAnchor) {
                Cue.ANCHOR_TYPE_END -> anchor - textWidth
                Cue.ANCHOR_TYPE_MIDDLE -> (anchor * 2 - textWidth) / 2
                else -> anchor
            }
            textLeft = max(left, 0)
            textRight = min(textLeft + textWidth, parentWidth)
        } else {
            textLeft = (parentWidth - textWidth) / 2
            textRight = textLeft + textWidth
        }
        textWidth = textRight - textLeft
        if (textWidth <= 0) return

        var textTop: Int
        if (cue.line != Cue.DIMEN_UNSET) {
            if (cue.lineType == Cue.LINE_TYPE_FRACTION) {
                val anchor = (parentHeight * cue.line).roundToInt()
                textTop = when (cue.lineAnchor) {
                    Cue.ANCHOR_TYPE_END -> anchor - textHeight
                    Cue.ANCHOR_TYPE_MIDDLE -> (anchor * 2 - textHeight) / 2
                    else -> anchor
                }
            } else {
                val firstLineHeight = layout.getLineBottom(0) - layout.getLineTop(0)
                textTop = if (cue.line >= 0) (cue.line * firstLineHeight).roundToInt()
                else ((cue.line + 1) * firstLineHeight).roundToInt() + parentHeight - textHeight
            }
            if (textTop + textHeight > parentHeight) textTop = parentHeight - textHeight
            else if (textTop < 0) textTop = 0
        } else {
            textTop = parentHeight - textHeight - (parentHeight * BOTTOM_PADDING_FRACTION).toInt()
        }

        layout = staticLayout(text, textWidth, alignment)
        val edgeLayout = staticLayout(edgeText, textWidth, alignment)
        val save = canvas.save()
        canvas.translate(textLeft.toFloat(), textTop.toFloat())
        textPaint.strokeJoin = Paint.Join.ROUND
        textPaint.strokeWidth = outlineWidth
        textPaint.color = android.graphics.Color.BLACK
        textPaint.style = Paint.Style.FILL_AND_STROKE
        edgeLayout.draw(canvas)
        textPaint.color = android.graphics.Color.WHITE
        textPaint.style = Paint.Style.FILL
        layout.draw(canvas)
        canvas.restoreToCount(save)
    }

    private fun drawBitmap(canvas: Canvas, cue: Cue, bitmap: android.graphics.Bitmap, parentWidth: Int, parentHeight: Int) {
        if (cue.position == Cue.DIMEN_UNSET || cue.line == Cue.DIMEN_UNSET || cue.size == Cue.DIMEN_UNSET) return
        val anchorX = parentWidth * cue.position
        val anchorY = parentHeight * cue.line
        val width = (parentWidth * cue.size).roundToInt()
        if (width <= 0 || bitmap.width <= 0) return
        val height = if (cue.bitmapHeight != Cue.DIMEN_UNSET) (parentHeight * cue.bitmapHeight).roundToInt()
        else (width * (bitmap.height.toFloat() / bitmap.width)).roundToInt()
        val x = when (cue.positionAnchor) {
            Cue.ANCHOR_TYPE_END -> anchorX - width
            Cue.ANCHOR_TYPE_MIDDLE -> anchorX - width / 2
            else -> anchorX
        }.roundToInt()
        val y = when (cue.lineAnchor) {
            Cue.ANCHOR_TYPE_END -> anchorY - height
            Cue.ANCHOR_TYPE_MIDDLE -> anchorY - height / 2
            else -> anchorY
        }.roundToInt()
        canvas.drawBitmap(bitmap, null, Rect(x, y, x + width, y + height), bitmapPaint)
    }

    /**
     * Right-to-left text is wrapped line by line with Unicode direction marks, exactly as Media3's
     * BidiUtils did, so neutral characters (punctuation at a line's edge) land where they always have.
     */
    private fun bidiWrap(text: String): String {
        if (!text.any { Character.getDirectionality(it).let { d -> d in RTL_DIRECTIONS } }) return text
        val eol = if (text.contains("\r\n")) "\r\n" else "\n"
        val formatter = BidiFormatter.getInstance()
        return text.split(eol).joinToString(eol) { formatter.unicodeWrap(it, TextDirectionHeuristics.LTR) }
    }

    private fun staticLayout(text: CharSequence, width: Int, alignment: Layout.Alignment): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(alignment)
            .setLineSpacing(0f, 1f)
            .setIncludePad(true)
            .build()

    private companion object {
        val RTL_DIRECTIONS = setOf(
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
        )
        const val INNER_PADDING_RATIO = 0.125f
        const val BOTTOM_PADDING_FRACTION = 0.08f
        val BOX_COLOR = android.graphics.Color.argb(160, 0, 0, 0)
    }
}
