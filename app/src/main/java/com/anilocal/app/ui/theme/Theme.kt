package com.anilocal.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.anilocal.app.ui.kit.LocalContentColor
import com.anilocal.app.ui.kit.LocalTextStyle
import com.anilocal.app.ui.kit.PressIndication

// Onboard palette — near-black surfaces with a midnight-blue accent, flat (hairlines, no shadows).
// The accent comes in three strengths: MidnightBlue is the workhorse (buttons, active states),
// MidnightDeep fills larger selected containers (chips, nav indicator, selected rows), and
// MidnightBright is for small accents that need pop on dark (links, live progress, the seek fill).
private val MidnightBlue = Color(0xFF2F4FAE)
private val MidnightDeep = Color(0xFF1B2A6B)
private val MidnightBright = Color(0xFF7C9AFF)

/**
 * The app's colour roles. The names follow the Material roles the screens were written against,
 * so a role means what it always meant; the values are the Onboard palette.
 */
@Immutable
class AppColors(
    val primary: Color = MidnightBlue,
    val onPrimary: Color = Color.White,
    val primaryContainer: Color = MidnightDeep,
    val onPrimaryContainer: Color = Color(0xFFDCE2FF),
    val tertiary: Color = MidnightBright,
    val onTertiary: Color = Color(0xFF0B1230),
    val background: Color = Color(0xFF0E0F13),
    val onBackground: Color = Color(0xFFECEDF1),
    val surface: Color = Color(0xFF16181F),
    val onSurface: Color = Color(0xFFECEDF1),
    val onSurfaceVariant: Color = Color(0xFF9AA0AE),
    val surfaceContainerLowest: Color = Color(0xFF0B0D11),
    val surfaceContainerLow: Color = Color(0xFF12141A),
    val surfaceContainer: Color = Color(0xFF16181F),
    val surfaceContainerHigh: Color = Color(0xFF1A1E27),
    val surfaceContainerHighest: Color = Color(0xFF1E212B),
    val outline: Color = Color(0xFF3A404E),
    val outlineVariant: Color = Color(0xFF262B36),
    /** Unfilled tracks: switch, slider, progress. */
    val track: Color = Color(0xFF2A2F3B),
    /** Text and icons on a disabled control. */
    val disabled: Color = Color(0xFF5D6371),
    val error: Color = Color(0xFFFF8A80),
    /** Fill behind a destructive action's label ("Remove"). */
    val errorContainer: Color = Color(0xFF3A1F24),
)

/** The app's type roles: system Roboto, heavy titles over minimal chrome. */
@Immutable
class AppTypography(
    val headlineMedium: TextStyle = style(28, 34, FontWeight.Bold, letterSpacing = -0.01f),
    val headlineSmall: TextStyle = style(24, 30, FontWeight.Bold),
    val titleLarge: TextStyle = style(20, 26, FontWeight.Bold),
    val titleMedium: TextStyle = style(17, 22, FontWeight.SemiBold),
    val titleSmall: TextStyle = style(15, 20, FontWeight.SemiBold),
    val bodyLarge: TextStyle = style(15, 21, FontWeight.Normal),
    val bodyMedium: TextStyle = style(14, 20, FontWeight.Normal),
    val bodySmall: TextStyle = style(12, 16, FontWeight.Normal),
    val labelLarge: TextStyle = style(14, 20, FontWeight.Medium),
    val labelMedium: TextStyle = style(12, 16, FontWeight.Medium),
    val labelSmall: TextStyle = style(11, 14, FontWeight.Medium),
) {
    private companion object {
        fun style(size: Int, line: Int, weight: FontWeight, letterSpacing: Float = 0f) = TextStyle(
            fontSize = size.sp,
            lineHeight = line.sp,
            fontWeight = weight,
            letterSpacing = letterSpacing.em,
            // Centre text in its line box and keep the full line height, as the Material roles did,
            // so swapping them in changes no layout but the sizes.
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
    }
}

private val LocalAppColors = staticCompositionLocalOf { AppColors() }
private val LocalAppTypography = staticCompositionLocalOf { AppTypography() }

/** The theme's values, read like `AppTheme.colors.primary` / `AppTheme.type.bodyLarge`. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
    val type: AppTypography
        @Composable @ReadOnlyComposable get() = LocalAppTypography.current
}

private val Colors = AppColors()
private val Type = AppTypography()
private val SelectionColors = TextSelectionColors(
    handleColor = MidnightBright,
    backgroundColor = MidnightBright.copy(alpha = 0.35f),
)

/**
 * Dark-only, like the reference app: the screens draw white brand chrome over dark art and
 * scrims, which a light theme would render illegible. Presses show [PressIndication] (a quick
 * tint, no ripple) on everything clickable unless a component asks for its own.
 */
@Composable
fun AniLocalTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Transparent so edge-to-edge art (Home hero, Details header) bleeds under the bar;
            // screens that don't go edge-to-edge pad themselves with statusBarsPadding().
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    CompositionLocalProvider(
        LocalAppColors provides Colors,
        LocalAppTypography provides Type,
        LocalContentColor provides Colors.onBackground,
        LocalTextStyle provides Type.bodyLarge,
        LocalIndication provides PressIndication.Default,
        LocalTextSelectionColors provides SelectionColors,
        content = content,
    )
}
