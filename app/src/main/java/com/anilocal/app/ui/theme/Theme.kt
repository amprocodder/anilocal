package com.anilocal.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat

// Onboard palette — AniLab-style near-black surfaces with a midnight-blue accent.
// The accent comes in three strengths: MidnightBlue is the workhorse (buttons, seek fill, active
// states), MidnightDeep fills larger selected containers (chips, nav indicator), and
// MidnightBright is for small accents that need pop on dark ("See all" links, live progress).
private val MidnightBlue = Color(0xFF2F4FAE)
private val MidnightDeep = Color(0xFF1B2A6B)
private val MidnightBright = Color(0xFF7C9AFF)
private val Bg = Color(0xFF0E0F13)
private val Surface = Color(0xFF16181F)
private val SurfaceVariant = Color(0xFF1E212B)
private val OnBg = Color(0xFFECEDF1)
private val OnMuted = Color(0xFF9AA0AE)

private val DarkColors = darkColorScheme(
    primary = MidnightBlue,
    onPrimary = Color.White,
    primaryContainer = MidnightDeep,
    onPrimaryContainer = Color(0xFFDCE2FF),
    secondary = MidnightDeep,
    onSecondary = Color.White,
    secondaryContainer = MidnightDeep,
    onSecondaryContainer = Color(0xFFDCE2FF),
    tertiary = MidnightBright,
    onTertiary = Color(0xFF0B1230),
    background = Bg,
    onBackground = OnBg,
    surface = Surface,
    onSurface = OnBg,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnMuted,
    surfaceContainerLowest = Color(0xFF0B0D11),
    surfaceContainerLow = Color(0xFF12141A),
    surfaceContainer = Color(0xFF171A22),
    surfaceContainerHigh = Color(0xFF1A1E27),
    surfaceContainerHighest = SurfaceVariant,
    outline = Color(0xFF3A404E),
    outlineVariant = Color(0xFF262B36),
)

private val LightColors = lightColorScheme(
    primary = MidnightBlue,
    onPrimary = Color.White,
    secondary = MidnightDeep,
    tertiary = MidnightBright,
)

// Stock Roboto, but bold title/headline roles — the poster-forward AniLab look leans on heavy
// titles over otherwise minimal chrome.
private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
fun AniLocalTheme(
    // Dark-only by default, like the reference app: the redesign draws hard-coded white brand
    // chrome over dark art/scrims, which a system light theme would render illegible.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Transparent so edge-to-edge art (Home hero, Details header) bleeds under the bar;
            // screens that don't go edge-to-edge pad themselves with statusBarsPadding().
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
