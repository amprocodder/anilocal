package com.anilocal.app.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.anilocal.app.ui.theme.AppColors
import com.anilocal.app.ui.theme.AppTheme

/** The colour text and icons take when nothing sets one: provided by the theme and by [Surface]. */
val LocalContentColor = compositionLocalOf { Color.Unspecified }

/** The text style [Text] uses when none is passed. */
val LocalTextStyle = compositionLocalOf { TextStyle.Default }

/**
 * Text in the app's type. Colour resolves as: [color], else the style's colour, else
 * [LocalContentColor] — so text inherits its container's content colour.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = mergeStyle(style, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration, textAlign, lineHeight),
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = mergeStyle(style, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration, textAlign, lineHeight),
        onTextLayout = onTextLayout ?: {},
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        inlineContent = inlineContent,
    )
}

@Composable
private fun mergeStyle(
    style: TextStyle,
    color: Color,
    fontSize: TextUnit,
    fontStyle: FontStyle?,
    fontWeight: FontWeight?,
    fontFamily: FontFamily?,
    letterSpacing: TextUnit,
    textDecoration: TextDecoration?,
    textAlign: TextAlign?,
    lineHeight: TextUnit,
): TextStyle = style.merge(
    color = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } },
    fontSize = fontSize,
    fontWeight = fontWeight,
    textAlign = textAlign ?: TextAlign.Unspecified,
    lineHeight = lineHeight,
    fontFamily = fontFamily,
    textDecoration = textDecoration,
    fontStyle = fontStyle,
    letterSpacing = letterSpacing,
)

/** Sets the style [Text] picks up inside [content], merged over the current one. */
@Composable
fun ProvideTextStyle(value: TextStyle, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.merge(value), content = content)
}

/**
 * A vector icon tinted with [tint] ([LocalContentColor] by default), at its own size (24dp for the
 * app's glyphs) unless the modifier sizes it.
 */
@Composable
@NonRestartableComposable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val painter = rememberVectorPainter(imageVector)
    val semantics = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier
    }
    Box(
        modifier
            .then(semantics)
            .paint(
                painter,
                colorFilter = if (tint.isSpecified) ColorFilter.tint(tint) else null,
                contentScale = ContentScale.Fit,
            ),
    )
}

/** The content colour that reads on [color], for the palette's container colours. */
@Composable
fun contentColorFor(color: Color): Color {
    val c: AppColors = AppTheme.colors
    return when (color) {
        c.primary -> c.onPrimary
        c.primaryContainer -> c.onPrimaryContainer
        c.tertiary -> c.onTertiary
        c.background -> c.onBackground
        c.surface, c.surfaceContainerLowest, c.surfaceContainerLow, c.surfaceContainer,
        c.surfaceContainerHigh, c.surfaceContainerHighest -> c.onSurface
        else -> LocalContentColor.current
    }
}

/**
 * A container: [color] filled into [shape], an optional [border], and content tinted
 * [contentColor]. It takes the touches that land on it (they don't fall through to what's behind),
 * like any card or panel should.
 */
@Composable
fun Surface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = AppTheme.colors.surface,
    contentColor: Color = contentColorFor(color),
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .surface(shape, color, border)
            .semantics(mergeDescendants = false) { isTraversalGroup = true }
            .pointerInput(Unit) {},
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}

/** A clickable [Surface]: a card or pill that is itself the button. */
@Composable
fun Surface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = AppTheme.colors.surface,
    contentColor: Color = contentColorFor(color),
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .surface(shape, color, border)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}

private fun Modifier.surface(shape: Shape, color: Color, border: BorderStroke?): Modifier =
    then(if (border != null) Modifier.border(border, shape) else Modifier)
        .background(color, shape)
        .clip(shape)

/** A hairline rule. */
@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = AppTheme.colors.outlineVariant,
) {
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}
