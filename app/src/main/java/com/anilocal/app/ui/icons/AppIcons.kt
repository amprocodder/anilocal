/*
 * Icon path data copied from the Material Design icons as published in AndroidX Compose
 * (androidx.compose.material:material-icons-{core,extended} 1.7.x):
 *   Copyright 2024 The Android Open Source Project
 *   Licensed under the Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0)
 * Only the glyphs this app draws are included, so the multi-megabyte icon library isn't shipped.
 */
package com.anilocal.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The app's icon set: every glyph is a 24dp vector built on first use (lazily) and cached.
 * Grouped like Material's own sets (Filled / Outlined / AutoMirrored) so call sites read the same.
 * Tinted by whatever draws it (`Icon` uses LocalContentColor), so the black fill is only a mask.
 */
object AppIcons {
    object Filled {
        val AspectRatio: ImageVector by lazy {
            icon("Filled.AspectRatio") {
                path {
                    moveTo(19.0f, 12.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(3.0f)
                    horizontalLineToRelative(-3.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(5.0f)
                    verticalLineToRelative(-5.0f)
                    close()
                    moveTo(7.0f, 9.0f)
                    horizontalLineToRelative(3.0f)
                    lineTo(10.0f, 7.0f)
                    lineTo(5.0f, 7.0f)
                    verticalLineToRelative(5.0f)
                    horizontalLineToRelative(2.0f)
                    lineTo(7.0f, 9.0f)
                    close()
                    moveTo(21.0f, 3.0f)
                    lineTo(3.0f, 3.0f)
                    curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    verticalLineToRelative(14.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(18.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    lineTo(23.0f, 5.0f)
                    curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                    close()
                    moveTo(21.0f, 19.01f)
                    lineTo(3.0f, 19.01f)
                    lineTo(3.0f, 4.99f)
                    horizontalLineToRelative(18.0f)
                    verticalLineToRelative(14.02f)
                    close()
                }

            }
        }

        val BookmarkAdded: ImageVector by lazy {
            icon("Filled.BookmarkAdded") {
                path {
                    moveTo(19.0f, 21.0f)
                    lineToRelative(-7.0f, -3.0f)
                    lineToRelative(-7.0f, 3.0f)
                    verticalLineTo(5.0f)
                    curveToRelative(0.0f, -1.1f, 0.9f, -2.0f, 2.0f, -2.0f)
                    lineToRelative(7.0f, 0.0f)
                    curveToRelative(-0.63f, 0.84f, -1.0f, 1.87f, -1.0f, 3.0f)
                    curveToRelative(0.0f, 2.76f, 2.24f, 5.0f, 5.0f, 5.0f)
                    curveToRelative(0.34f, 0.0f, 0.68f, -0.03f, 1.0f, -0.1f)
                    verticalLineTo(21.0f)
                    close()
                    moveTo(17.83f, 9.0f)
                    lineTo(15.0f, 6.17f)
                    lineToRelative(1.41f, -1.41f)
                    lineToRelative(1.41f, 1.41f)
                    lineToRelative(3.54f, -3.54f)
                    lineToRelative(1.41f, 1.41f)
                    lineTo(17.83f, 9.0f)
                    close()
                }

            }
        }

        val Check: ImageVector by lazy {
            icon("Filled.Check") {
                path {
                    moveTo(9.0f, 16.17f)
                    lineTo(4.83f, 12.0f)
                    lineToRelative(-1.42f, 1.41f)
                    lineTo(9.0f, 19.0f)
                    lineTo(21.0f, 7.0f)
                    lineToRelative(-1.41f, -1.41f)
                    close()
                }

            }
        }

        val Close: ImageVector by lazy {
            icon("Filled.Close") {
                path {
                    moveTo(19.0f, 6.41f)
                    lineTo(17.59f, 5.0f)
                    lineTo(12.0f, 10.59f)
                    lineTo(6.41f, 5.0f)
                    lineTo(5.0f, 6.41f)
                    lineTo(10.59f, 12.0f)
                    lineTo(5.0f, 17.59f)
                    lineTo(6.41f, 19.0f)
                    lineTo(12.0f, 13.41f)
                    lineTo(17.59f, 19.0f)
                    lineTo(19.0f, 17.59f)
                    lineTo(13.41f, 12.0f)
                    close()
                }

            }
        }

        val ClosedCaption: ImageVector by lazy {
            icon("Filled.ClosedCaption") {
                path {
                    moveTo(19.0f, 4.0f)
                    lineTo(5.0f, 4.0f)
                    curveToRelative(-1.11f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    verticalLineToRelative(12.0f)
                    curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(14.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    lineTo(21.0f, 6.0f)
                    curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                    close()
                    moveTo(11.0f, 11.0f)
                    lineTo(9.5f, 11.0f)
                    verticalLineToRelative(-0.5f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(3.0f)
                    horizontalLineToRelative(2.0f)
                    lineTo(9.5f, 13.0f)
                    lineTo(11.0f, 13.0f)
                    verticalLineToRelative(1.0f)
                    curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                    lineTo(7.0f, 15.0f)
                    curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                    verticalLineToRelative(-4.0f)
                    curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                    horizontalLineToRelative(3.0f)
                    curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                    verticalLineToRelative(1.0f)
                    close()
                    moveTo(18.0f, 11.0f)
                    horizontalLineToRelative(-1.5f)
                    verticalLineToRelative(-0.5f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(3.0f)
                    horizontalLineToRelative(2.0f)
                    lineTo(16.5f, 13.0f)
                    lineTo(18.0f, 13.0f)
                    verticalLineToRelative(1.0f)
                    curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                    horizontalLineToRelative(-3.0f)
                    curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                    verticalLineToRelative(-4.0f)
                    curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                    horizontalLineToRelative(3.0f)
                    curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                    verticalLineToRelative(1.0f)
                    close()
                }

            }
        }

        val Delete: ImageVector by lazy {
            icon("Filled.Delete") {
                path {
                    moveTo(6.0f, 19.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(8.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    verticalLineTo(7.0f)
                    horizontalLineTo(6.0f)
                    verticalLineToRelative(12.0f)
                    close()
                    moveTo(19.0f, 4.0f)
                    horizontalLineToRelative(-3.5f)
                    lineToRelative(-1.0f, -1.0f)
                    horizontalLineToRelative(-5.0f)
                    lineToRelative(-1.0f, 1.0f)
                    horizontalLineTo(5.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(14.0f)
                    verticalLineTo(4.0f)
                    close()
                }

            }
        }

        val Download: ImageVector by lazy {
            icon("Filled.Download") {
                path {
                    moveTo(5.0f, 20.0f)
                    horizontalLineToRelative(14.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineTo(5.0f)
                    verticalLineTo(20.0f)
                    close()
                    moveTo(19.0f, 9.0f)
                    horizontalLineToRelative(-4.0f)
                    verticalLineTo(3.0f)
                    horizontalLineTo(9.0f)
                    verticalLineToRelative(6.0f)
                    horizontalLineTo(5.0f)
                    lineToRelative(7.0f, 7.0f)
                    lineTo(19.0f, 9.0f)
                    close()
                }

            }
        }

        val DownloadDone: ImageVector by lazy {
            icon("Filled.DownloadDone") {
                path {
                    moveTo(20.13f, 5.41f)
                    lineToRelative(-1.41f, -1.41f)
                    lineToRelative(-9.19f, 9.19f)
                    lineToRelative(-4.25f, -4.24f)
                    lineToRelative(-1.41f, 1.41f)
                    lineToRelative(5.66f, 5.66f)
                    close()
                }
                path {
                    moveTo(5.0f, 18.0f)
                    horizontalLineToRelative(14.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(-14.0f)
                    close()
                }

            }
        }

        val Equalizer: ImageVector by lazy {
            icon("Filled.Equalizer") {
                path {
                    moveTo(10.0f, 20.0f)
                    horizontalLineToRelative(4.0f)
                    lineTo(14.0f, 4.0f)
                    horizontalLineToRelative(-4.0f)
                    verticalLineToRelative(16.0f)
                    close()
                    moveTo(4.0f, 20.0f)
                    horizontalLineToRelative(4.0f)
                    verticalLineToRelative(-8.0f)
                    lineTo(4.0f, 12.0f)
                    verticalLineToRelative(8.0f)
                    close()
                    moveTo(16.0f, 9.0f)
                    verticalLineToRelative(11.0f)
                    horizontalLineToRelative(4.0f)
                    lineTo(20.0f, 9.0f)
                    horizontalLineToRelative(-4.0f)
                    close()
                }

            }
        }

        val ExpandLess: ImageVector by lazy {
            icon("Filled.ExpandLess") {
                path {
                    moveTo(12.0f, 8.0f)
                    lineToRelative(-6.0f, 6.0f)
                    lineToRelative(1.41f, 1.41f)
                    lineTo(12.0f, 10.83f)
                    lineToRelative(4.59f, 4.58f)
                    lineTo(18.0f, 14.0f)
                    close()
                }

            }
        }

        val ExpandMore: ImageVector by lazy {
            icon("Filled.ExpandMore") {
                path {
                    moveTo(16.59f, 8.59f)
                    lineTo(12.0f, 13.17f)
                    lineTo(7.41f, 8.59f)
                    lineTo(6.0f, 10.0f)
                    lineToRelative(6.0f, 6.0f)
                    lineToRelative(6.0f, -6.0f)
                    close()
                }

            }
        }

        val Forward10: ImageVector by lazy {
            icon("Filled.Forward10") {
                path {
                    moveTo(18.0f, 13.0f)
                    curveToRelative(0.0f, 3.31f, -2.69f, 6.0f, -6.0f, 6.0f)
                    reflectiveCurveToRelative(-6.0f, -2.69f, -6.0f, -6.0f)
                    reflectiveCurveToRelative(2.69f, -6.0f, 6.0f, -6.0f)
                    verticalLineToRelative(4.0f)
                    lineToRelative(5.0f, -5.0f)
                    lineToRelative(-5.0f, -5.0f)
                    verticalLineToRelative(4.0f)
                    curveToRelative(-4.42f, 0.0f, -8.0f, 3.58f, -8.0f, 8.0f)
                    curveToRelative(0.0f, 4.42f, 3.58f, 8.0f, 8.0f, 8.0f)
                    reflectiveCurveToRelative(8.0f, -3.58f, 8.0f, -8.0f)
                    horizontalLineTo(18.0f)
                    close()
                }
                path {
                    moveTo(10.86f, 15.94f)
                    lineToRelative(0.0f, -4.27f)
                    lineToRelative(-0.09f, 0.0f)
                    lineToRelative(-1.77f, 0.63f)
                    lineToRelative(0.0f, 0.69f)
                    lineToRelative(1.01f, -0.31f)
                    lineToRelative(0.0f, 3.26f)
                    close()
                }
                path {
                    moveTo(12.25f, 13.44f)
                    verticalLineToRelative(0.74f)
                    curveToRelative(0.0f, 1.9f, 1.31f, 1.82f, 1.44f, 1.82f)
                    curveToRelative(0.14f, 0.0f, 1.44f, 0.09f, 1.44f, -1.82f)
                    verticalLineToRelative(-0.74f)
                    curveToRelative(0.0f, -1.9f, -1.31f, -1.82f, -1.44f, -1.82f)
                    curveTo(13.55f, 11.62f, 12.25f, 11.53f, 12.25f, 13.44f)
                    close()
                    moveTo(14.29f, 13.32f)
                    verticalLineToRelative(0.97f)
                    curveToRelative(0.0f, 0.77f, -0.21f, 1.03f, -0.59f, 1.03f)
                    curveToRelative(-0.38f, 0.0f, -0.6f, -0.26f, -0.6f, -1.03f)
                    verticalLineToRelative(-0.97f)
                    curveToRelative(0.0f, -0.75f, 0.22f, -1.01f, 0.59f, -1.01f)
                    curveTo(14.07f, 12.3f, 14.29f, 12.57f, 14.29f, 13.32f)
                    close()
                }

            }
        }

        val Fullscreen: ImageVector by lazy {
            icon("Filled.Fullscreen") {
                path {
                    moveTo(7.0f, 14.0f)
                    lineTo(5.0f, 14.0f)
                    verticalLineToRelative(5.0f)
                    horizontalLineToRelative(5.0f)
                    verticalLineToRelative(-2.0f)
                    lineTo(7.0f, 17.0f)
                    verticalLineToRelative(-3.0f)
                    close()
                    moveTo(5.0f, 10.0f)
                    horizontalLineToRelative(2.0f)
                    lineTo(7.0f, 7.0f)
                    horizontalLineToRelative(3.0f)
                    lineTo(10.0f, 5.0f)
                    lineTo(5.0f, 5.0f)
                    verticalLineToRelative(5.0f)
                    close()
                    moveTo(17.0f, 17.0f)
                    horizontalLineToRelative(-3.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(5.0f)
                    verticalLineToRelative(-5.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(3.0f)
                    close()
                    moveTo(14.0f, 5.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(3.0f)
                    verticalLineToRelative(3.0f)
                    horizontalLineToRelative(2.0f)
                    lineTo(19.0f, 5.0f)
                    horizontalLineToRelative(-5.0f)
                    close()
                }

            }
        }

        val FullscreenExit: ImageVector by lazy {
            icon("Filled.FullscreenExit") {
                path {
                    moveTo(5.0f, 16.0f)
                    horizontalLineToRelative(3.0f)
                    verticalLineToRelative(3.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(-5.0f)
                    lineTo(5.0f, 14.0f)
                    verticalLineToRelative(2.0f)
                    close()
                    moveTo(8.0f, 8.0f)
                    lineTo(5.0f, 8.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(5.0f)
                    lineTo(10.0f, 5.0f)
                    lineTo(8.0f, 5.0f)
                    verticalLineToRelative(3.0f)
                    close()
                    moveTo(14.0f, 19.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(-3.0f)
                    horizontalLineToRelative(3.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(-5.0f)
                    verticalLineToRelative(5.0f)
                    close()
                    moveTo(16.0f, 8.0f)
                    lineTo(16.0f, 5.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(5.0f)
                    horizontalLineToRelative(5.0f)
                    lineTo(19.0f, 8.0f)
                    horizontalLineToRelative(-3.0f)
                    close()
                }

            }
        }

        val KeyboardArrowDown: ImageVector by lazy {
            icon("Filled.KeyboardArrowDown") {
                path {
                    moveTo(7.41f, 8.59f)
                    lineTo(12.0f, 13.17f)
                    lineToRelative(4.59f, -4.58f)
                    lineTo(18.0f, 10.0f)
                    lineToRelative(-6.0f, 6.0f)
                    lineToRelative(-6.0f, -6.0f)
                    lineToRelative(1.41f, -1.41f)
                    close()
                }

            }
        }

        val Lock: ImageVector by lazy {
            icon("Filled.Lock") {
                path {
                    moveTo(18.0f, 8.0f)
                    horizontalLineToRelative(-1.0f)
                    lineTo(17.0f, 6.0f)
                    curveToRelative(0.0f, -2.76f, -2.24f, -5.0f, -5.0f, -5.0f)
                    reflectiveCurveTo(7.0f, 3.24f, 7.0f, 6.0f)
                    verticalLineToRelative(2.0f)
                    lineTo(6.0f, 8.0f)
                    curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    verticalLineToRelative(10.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(12.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    lineTo(20.0f, 10.0f)
                    curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                    close()
                    moveTo(12.0f, 17.0f)
                    curveToRelative(-1.1f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                    reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                    reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                    reflectiveCurveToRelative(-0.9f, 2.0f, -2.0f, 2.0f)
                    close()
                    moveTo(15.1f, 8.0f)
                    lineTo(8.9f, 8.0f)
                    lineTo(8.9f, 6.0f)
                    curveToRelative(0.0f, -1.71f, 1.39f, -3.1f, 3.1f, -3.1f)
                    curveToRelative(1.71f, 0.0f, 3.1f, 1.39f, 3.1f, 3.1f)
                    verticalLineToRelative(2.0f)
                    close()
                }

            }
        }

        val LockOpen: ImageVector by lazy {
            icon("Filled.LockOpen") {
                path {
                    moveTo(12.0f, 17.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                    reflectiveCurveToRelative(-2.0f, 0.9f, -2.0f, 2.0f)
                    reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                    close()
                    moveTo(18.0f, 8.0f)
                    horizontalLineToRelative(-1.0f)
                    lineTo(17.0f, 6.0f)
                    curveToRelative(0.0f, -2.76f, -2.24f, -5.0f, -5.0f, -5.0f)
                    reflectiveCurveTo(7.0f, 3.24f, 7.0f, 6.0f)
                    horizontalLineToRelative(1.9f)
                    curveToRelative(0.0f, -1.71f, 1.39f, -3.1f, 3.1f, -3.1f)
                    curveToRelative(1.71f, 0.0f, 3.1f, 1.39f, 3.1f, 3.1f)
                    verticalLineToRelative(2.0f)
                    lineTo(6.0f, 8.0f)
                    curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    verticalLineToRelative(10.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(12.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    lineTo(20.0f, 10.0f)
                    curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                    close()
                    moveTo(18.0f, 20.0f)
                    lineTo(6.0f, 20.0f)
                    lineTo(6.0f, 10.0f)
                    horizontalLineToRelative(12.0f)
                    verticalLineToRelative(10.0f)
                    close()
                }

            }
        }

        val Pause: ImageVector by lazy {
            icon("Filled.Pause") {
                path {
                    moveTo(6.0f, 19.0f)
                    horizontalLineToRelative(4.0f)
                    lineTo(10.0f, 5.0f)
                    lineTo(6.0f, 5.0f)
                    verticalLineToRelative(14.0f)
                    close()
                    moveTo(14.0f, 5.0f)
                    verticalLineToRelative(14.0f)
                    horizontalLineToRelative(4.0f)
                    lineTo(18.0f, 5.0f)
                    horizontalLineToRelative(-4.0f)
                    close()
                }

            }
        }

        val PlayArrow: ImageVector by lazy {
            icon("Filled.PlayArrow") {
                path {
                    moveTo(8.0f, 5.0f)
                    verticalLineToRelative(14.0f)
                    lineToRelative(11.0f, -7.0f)
                    close()
                }

            }
        }

        val Refresh: ImageVector by lazy {
            icon("Filled.Refresh") {
                path {
                    moveTo(17.65f, 6.35f)
                    curveTo(16.2f, 4.9f, 14.21f, 4.0f, 12.0f, 4.0f)
                    curveToRelative(-4.42f, 0.0f, -7.99f, 3.58f, -7.99f, 8.0f)
                    reflectiveCurveToRelative(3.57f, 8.0f, 7.99f, 8.0f)
                    curveToRelative(3.73f, 0.0f, 6.84f, -2.55f, 7.73f, -6.0f)
                    horizontalLineToRelative(-2.08f)
                    curveToRelative(-0.82f, 2.33f, -3.04f, 4.0f, -5.65f, 4.0f)
                    curveToRelative(-3.31f, 0.0f, -6.0f, -2.69f, -6.0f, -6.0f)
                    reflectiveCurveToRelative(2.69f, -6.0f, 6.0f, -6.0f)
                    curveToRelative(1.66f, 0.0f, 3.14f, 0.69f, 4.22f, 1.78f)
                    lineTo(13.0f, 11.0f)
                    horizontalLineToRelative(7.0f)
                    verticalLineTo(4.0f)
                    lineToRelative(-2.35f, 2.35f)
                    close()
                }

            }
        }

        val Replay10: ImageVector by lazy {
            icon("Filled.Replay10") {
                path {
                    moveTo(11.99f, 5.0f)
                    verticalLineTo(1.0f)
                    lineToRelative(-5.0f, 5.0f)
                    lineToRelative(5.0f, 5.0f)
                    verticalLineTo(7.0f)
                    curveToRelative(3.31f, 0.0f, 6.0f, 2.69f, 6.0f, 6.0f)
                    reflectiveCurveToRelative(-2.69f, 6.0f, -6.0f, 6.0f)
                    reflectiveCurveToRelative(-6.0f, -2.69f, -6.0f, -6.0f)
                    horizontalLineToRelative(-2.0f)
                    curveToRelative(0.0f, 4.42f, 3.58f, 8.0f, 8.0f, 8.0f)
                    reflectiveCurveToRelative(8.0f, -3.58f, 8.0f, -8.0f)
                    reflectiveCurveTo(16.41f, 5.0f, 11.99f, 5.0f)
                    close()
                }
                path {
                    moveTo(10.89f, 16.0f)
                    horizontalLineToRelative(-0.85f)
                    verticalLineToRelative(-3.26f)
                    lineToRelative(-1.01f, 0.31f)
                    verticalLineToRelative(-0.69f)
                    lineToRelative(1.77f, -0.63f)
                    horizontalLineToRelative(0.09f)
                    verticalLineTo(16.0f)
                    close()
                }
                path {
                    moveTo(15.17f, 14.24f)
                    curveToRelative(0.0f, 0.32f, -0.03f, 0.6f, -0.1f, 0.82f)
                    reflectiveCurveToRelative(-0.17f, 0.42f, -0.29f, 0.57f)
                    reflectiveCurveToRelative(-0.28f, 0.26f, -0.45f, 0.33f)
                    reflectiveCurveToRelative(-0.37f, 0.1f, -0.59f, 0.1f)
                    reflectiveCurveToRelative(-0.41f, -0.03f, -0.59f, -0.1f)
                    reflectiveCurveToRelative(-0.33f, -0.18f, -0.46f, -0.33f)
                    reflectiveCurveToRelative(-0.23f, -0.34f, -0.3f, -0.57f)
                    reflectiveCurveToRelative(-0.11f, -0.5f, -0.11f, -0.82f)
                    verticalLineTo(13.5f)
                    curveToRelative(0.0f, -0.32f, 0.03f, -0.6f, 0.1f, -0.82f)
                    reflectiveCurveToRelative(0.17f, -0.42f, 0.29f, -0.57f)
                    reflectiveCurveToRelative(0.28f, -0.26f, 0.45f, -0.33f)
                    reflectiveCurveToRelative(0.37f, -0.1f, 0.59f, -0.1f)
                    reflectiveCurveToRelative(0.41f, 0.03f, 0.59f, 0.1f)
                    curveToRelative(0.18f, 0.07f, 0.33f, 0.18f, 0.46f, 0.33f)
                    reflectiveCurveToRelative(0.23f, 0.34f, 0.3f, 0.57f)
                    reflectiveCurveToRelative(0.11f, 0.5f, 0.11f, 0.82f)
                    verticalLineTo(14.24f)
                    close()
                    moveTo(14.32f, 13.38f)
                    curveToRelative(0.0f, -0.19f, -0.01f, -0.35f, -0.04f, -0.48f)
                    reflectiveCurveToRelative(-0.07f, -0.23f, -0.12f, -0.31f)
                    reflectiveCurveToRelative(-0.11f, -0.14f, -0.19f, -0.17f)
                    reflectiveCurveToRelative(-0.16f, -0.05f, -0.25f, -0.05f)
                    reflectiveCurveToRelative(-0.18f, 0.02f, -0.25f, 0.05f)
                    reflectiveCurveToRelative(-0.14f, 0.09f, -0.19f, 0.17f)
                    reflectiveCurveToRelative(-0.09f, 0.18f, -0.12f, 0.31f)
                    reflectiveCurveToRelative(-0.04f, 0.29f, -0.04f, 0.48f)
                    verticalLineToRelative(0.97f)
                    curveToRelative(0.0f, 0.19f, 0.01f, 0.35f, 0.04f, 0.48f)
                    reflectiveCurveToRelative(0.07f, 0.24f, 0.12f, 0.32f)
                    reflectiveCurveToRelative(0.11f, 0.14f, 0.19f, 0.17f)
                    reflectiveCurveToRelative(0.16f, 0.05f, 0.25f, 0.05f)
                    reflectiveCurveToRelative(0.18f, -0.02f, 0.25f, -0.05f)
                    reflectiveCurveToRelative(0.14f, -0.09f, 0.19f, -0.17f)
                    reflectiveCurveToRelative(0.09f, -0.19f, 0.11f, -0.32f)
                    reflectiveCurveToRelative(0.04f, -0.29f, 0.04f, -0.48f)
                    verticalLineTo(13.38f)
                    close()
                }

            }
        }

        val Search: ImageVector by lazy {
            icon("Filled.Search") {
                path {
                    moveTo(15.5f, 14.0f)
                    horizontalLineToRelative(-0.79f)
                    lineToRelative(-0.28f, -0.27f)
                    curveTo(15.41f, 12.59f, 16.0f, 11.11f, 16.0f, 9.5f)
                    curveTo(16.0f, 5.91f, 13.09f, 3.0f, 9.5f, 3.0f)
                    reflectiveCurveTo(3.0f, 5.91f, 3.0f, 9.5f)
                    reflectiveCurveTo(5.91f, 16.0f, 9.5f, 16.0f)
                    curveToRelative(1.61f, 0.0f, 3.09f, -0.59f, 4.23f, -1.57f)
                    lineToRelative(0.27f, 0.28f)
                    verticalLineToRelative(0.79f)
                    lineToRelative(5.0f, 4.99f)
                    lineTo(20.49f, 19.0f)
                    lineToRelative(-4.99f, -5.0f)
                    close()
                    moveTo(9.5f, 14.0f)
                    curveTo(7.01f, 14.0f, 5.0f, 11.99f, 5.0f, 9.5f)
                    reflectiveCurveTo(7.01f, 5.0f, 9.5f, 5.0f)
                    reflectiveCurveTo(14.0f, 7.01f, 14.0f, 9.5f)
                    reflectiveCurveTo(11.99f, 14.0f, 9.5f, 14.0f)
                    close()
                }

            }
        }

        val Settings: ImageVector by lazy {
            icon("Filled.Settings") {
                path {
                    moveTo(19.14f, 12.94f)
                    curveToRelative(0.04f, -0.3f, 0.06f, -0.61f, 0.06f, -0.94f)
                    curveToRelative(0.0f, -0.32f, -0.02f, -0.64f, -0.07f, -0.94f)
                    lineToRelative(2.03f, -1.58f)
                    curveToRelative(0.18f, -0.14f, 0.23f, -0.41f, 0.12f, -0.61f)
                    lineToRelative(-1.92f, -3.32f)
                    curveToRelative(-0.12f, -0.22f, -0.37f, -0.29f, -0.59f, -0.22f)
                    lineToRelative(-2.39f, 0.96f)
                    curveToRelative(-0.5f, -0.38f, -1.03f, -0.7f, -1.62f, -0.94f)
                    lineTo(14.4f, 2.81f)
                    curveToRelative(-0.04f, -0.24f, -0.24f, -0.41f, -0.48f, -0.41f)
                    horizontalLineToRelative(-3.84f)
                    curveToRelative(-0.24f, 0.0f, -0.43f, 0.17f, -0.47f, 0.41f)
                    lineTo(9.25f, 5.35f)
                    curveTo(8.66f, 5.59f, 8.12f, 5.92f, 7.63f, 6.29f)
                    lineTo(5.24f, 5.33f)
                    curveToRelative(-0.22f, -0.08f, -0.47f, 0.0f, -0.59f, 0.22f)
                    lineTo(2.74f, 8.87f)
                    curveTo(2.62f, 9.08f, 2.66f, 9.34f, 2.86f, 9.48f)
                    lineToRelative(2.03f, 1.58f)
                    curveTo(4.84f, 11.36f, 4.8f, 11.69f, 4.8f, 12.0f)
                    reflectiveCurveToRelative(0.02f, 0.64f, 0.07f, 0.94f)
                    lineToRelative(-2.03f, 1.58f)
                    curveToRelative(-0.18f, 0.14f, -0.23f, 0.41f, -0.12f, 0.61f)
                    lineToRelative(1.92f, 3.32f)
                    curveToRelative(0.12f, 0.22f, 0.37f, 0.29f, 0.59f, 0.22f)
                    lineToRelative(2.39f, -0.96f)
                    curveToRelative(0.5f, 0.38f, 1.03f, 0.7f, 1.62f, 0.94f)
                    lineToRelative(0.36f, 2.54f)
                    curveToRelative(0.05f, 0.24f, 0.24f, 0.41f, 0.48f, 0.41f)
                    horizontalLineToRelative(3.84f)
                    curveToRelative(0.24f, 0.0f, 0.44f, -0.17f, 0.47f, -0.41f)
                    lineToRelative(0.36f, -2.54f)
                    curveToRelative(0.59f, -0.24f, 1.13f, -0.56f, 1.62f, -0.94f)
                    lineToRelative(2.39f, 0.96f)
                    curveToRelative(0.22f, 0.08f, 0.47f, 0.0f, 0.59f, -0.22f)
                    lineToRelative(1.92f, -3.32f)
                    curveToRelative(0.12f, -0.22f, 0.07f, -0.47f, -0.12f, -0.61f)
                    lineTo(19.14f, 12.94f)
                    close()
                    moveTo(12.0f, 15.6f)
                    curveToRelative(-1.98f, 0.0f, -3.6f, -1.62f, -3.6f, -3.6f)
                    reflectiveCurveToRelative(1.62f, -3.6f, 3.6f, -3.6f)
                    reflectiveCurveToRelative(3.6f, 1.62f, 3.6f, 3.6f)
                    reflectiveCurveTo(13.98f, 15.6f, 12.0f, 15.6f)
                    close()
                }

            }
        }

        val SkipNext: ImageVector by lazy {
            icon("Filled.SkipNext") {
                path {
                    moveTo(6.0f, 18.0f)
                    lineToRelative(8.5f, -6.0f)
                    lineTo(6.0f, 6.0f)
                    verticalLineToRelative(12.0f)
                    close()
                    moveTo(16.0f, 6.0f)
                    verticalLineToRelative(12.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineTo(6.0f)
                    horizontalLineToRelative(-2.0f)
                    close()
                }

            }
        }

        val SkipPrevious: ImageVector by lazy {
            icon("Filled.SkipPrevious") {
                path {
                    moveTo(6.0f, 6.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(12.0f)
                    lineTo(6.0f, 18.0f)
                    close()
                    moveTo(9.5f, 12.0f)
                    lineToRelative(8.5f, 6.0f)
                    lineTo(18.0f, 6.0f)
                    close()
                }

            }
        }

        val Speed: ImageVector by lazy {
            icon("Filled.Speed") {
                path {
                    moveTo(20.38f, 8.57f)
                    lineToRelative(-1.23f, 1.85f)
                    arcToRelative(8.0f, 8.0f, 0.0f, false, true, -0.22f, 7.58f)
                    lineTo(5.07f, 18.0f)
                    arcTo(8.0f, 8.0f, 0.0f, false, true, 15.58f, 6.85f)
                    lineToRelative(1.85f, -1.23f)
                    arcTo(10.0f, 10.0f, 0.0f, false, false, 3.35f, 19.0f)
                    arcToRelative(2.0f, 2.0f, 0.0f, false, false, 1.72f, 1.0f)
                    horizontalLineToRelative(13.85f)
                    arcToRelative(2.0f, 2.0f, 0.0f, false, false, 1.74f, -1.0f)
                    arcToRelative(10.0f, 10.0f, 0.0f, false, false, -0.27f, -10.44f)
                    close()
                    moveTo(10.59f, 15.41f)
                    arcToRelative(2.0f, 2.0f, 0.0f, false, false, 2.83f, 0.0f)
                    lineToRelative(5.66f, -8.49f)
                    lineToRelative(-8.49f, 5.66f)
                    arcToRelative(2.0f, 2.0f, 0.0f, false, false, 0.0f, 2.83f)
                    close()
                }

            }
        }
    }

    object Outlined {
        val AutoAwesome: ImageVector by lazy {
            icon("Outlined.AutoAwesome") {
                path {
                    moveTo(19.0f, 9.0f)
                    lineToRelative(1.25f, -2.75f)
                    lineToRelative(2.75f, -1.25f)
                    lineToRelative(-2.75f, -1.25f)
                    lineToRelative(-1.25f, -2.75f)
                    lineToRelative(-1.25f, 2.75f)
                    lineToRelative(-2.75f, 1.25f)
                    lineToRelative(2.75f, 1.25f)
                    close()
                }
                path {
                    moveTo(19.0f, 15.0f)
                    lineToRelative(-1.25f, 2.75f)
                    lineToRelative(-2.75f, 1.25f)
                    lineToRelative(2.75f, 1.25f)
                    lineToRelative(1.25f, 2.75f)
                    lineToRelative(1.25f, -2.75f)
                    lineToRelative(2.75f, -1.25f)
                    lineToRelative(-2.75f, -1.25f)
                    close()
                }
                path {
                    moveTo(11.5f, 9.5f)
                    lineTo(9.0f, 4.0f)
                    lineTo(6.5f, 9.5f)
                    lineTo(1.0f, 12.0f)
                    lineToRelative(5.5f, 2.5f)
                    lineTo(9.0f, 20.0f)
                    lineToRelative(2.5f, -5.5f)
                    lineTo(17.0f, 12.0f)
                    lineTo(11.5f, 9.5f)
                    close()
                    moveTo(9.99f, 12.99f)
                    lineTo(9.0f, 15.17f)
                    lineToRelative(-0.99f, -2.18f)
                    lineTo(5.83f, 12.0f)
                    lineToRelative(2.18f, -0.99f)
                    lineTo(9.0f, 8.83f)
                    lineToRelative(0.99f, 2.18f)
                    lineTo(12.17f, 12.0f)
                    lineTo(9.99f, 12.99f)
                    close()
                }

            }
        }

        val BookmarkAdd: ImageVector by lazy {
            icon("Outlined.BookmarkAdd") {
                path {
                    moveTo(17.0f, 11.0f)
                    verticalLineToRelative(6.97f)
                    lineToRelative(-5.0f, -2.14f)
                    lineToRelative(-5.0f, 2.14f)
                    verticalLineTo(5.0f)
                    horizontalLineToRelative(6.0f)
                    verticalLineTo(3.0f)
                    horizontalLineTo(7.0f)
                    curveTo(5.9f, 3.0f, 5.0f, 3.9f, 5.0f, 5.0f)
                    verticalLineToRelative(16.0f)
                    lineToRelative(7.0f, -3.0f)
                    lineToRelative(7.0f, 3.0f)
                    verticalLineTo(11.0f)
                    horizontalLineTo(17.0f)
                    close()
                    moveTo(21.0f, 7.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineTo(7.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineTo(5.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineTo(3.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineTo(7.0f)
                    close()
                }

            }
        }

        val BookmarkBorder: ImageVector by lazy {
            icon("Outlined.BookmarkBorder") {
                path {
                    moveTo(17.0f, 3.0f)
                    lineTo(7.0f, 3.0f)
                    curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    verticalLineToRelative(16.0f)
                    lineToRelative(7.0f, -3.0f)
                    lineToRelative(7.0f, 3.0f)
                    lineTo(19.0f, 5.0f)
                    curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                    close()
                    moveTo(17.0f, 18.0f)
                    lineToRelative(-5.0f, -2.18f)
                    lineTo(7.0f, 18.0f)
                    lineTo(7.0f, 5.0f)
                    horizontalLineToRelative(10.0f)
                    verticalLineToRelative(13.0f)
                    close()
                }

            }
        }

        val ClosedCaption: ImageVector by lazy {
            icon("Outlined.ClosedCaption") {
                path {
                    moveTo(19.0f, 4.0f)
                    lineTo(5.0f, 4.0f)
                    curveToRelative(-1.11f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    verticalLineToRelative(12.0f)
                    curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(14.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    lineTo(21.0f, 6.0f)
                    curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                    close()
                    moveTo(19.0f, 18.0f)
                    lineTo(5.0f, 18.0f)
                    lineTo(5.0f, 6.0f)
                    horizontalLineToRelative(14.0f)
                    verticalLineToRelative(12.0f)
                    close()
                    moveTo(7.0f, 15.0f)
                    horizontalLineToRelative(3.0f)
                    curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                    verticalLineToRelative(-1.0f)
                    lineTo(9.5f, 13.0f)
                    verticalLineToRelative(0.5f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(-3.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(0.5f)
                    lineTo(11.0f, 11.0f)
                    verticalLineToRelative(-1.0f)
                    curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                    lineTo(7.0f, 9.0f)
                    curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                    verticalLineToRelative(4.0f)
                    curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                    close()
                    moveTo(14.0f, 15.0f)
                    horizontalLineToRelative(3.0f)
                    curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                    verticalLineToRelative(-1.0f)
                    horizontalLineToRelative(-1.5f)
                    verticalLineToRelative(0.5f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(-3.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(0.5f)
                    lineTo(18.0f, 11.0f)
                    verticalLineToRelative(-1.0f)
                    curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                    horizontalLineToRelative(-3.0f)
                    curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                    verticalLineToRelative(4.0f)
                    curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                    close()
                }

            }
        }

        val Download: ImageVector by lazy {
            icon("Outlined.Download") {
                path {
                    moveTo(19.0f, 9.0f)
                    horizontalLineToRelative(-4.0f)
                    lineTo(15.0f, 3.0f)
                    lineTo(9.0f, 3.0f)
                    verticalLineToRelative(6.0f)
                    lineTo(5.0f, 9.0f)
                    lineToRelative(7.0f, 7.0f)
                    lineToRelative(7.0f, -7.0f)
                    close()
                    moveTo(11.0f, 11.0f)
                    lineTo(11.0f, 5.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(6.0f)
                    horizontalLineToRelative(1.17f)
                    lineTo(12.0f, 13.17f)
                    lineTo(9.83f, 11.0f)
                    lineTo(11.0f, 11.0f)
                    close()
                    moveTo(5.0f, 18.0f)
                    horizontalLineToRelative(14.0f)
                    verticalLineToRelative(2.0f)
                    lineTo(5.0f, 20.0f)
                    close()
                }

            }
        }

        val DownloadForOffline: ImageVector by lazy {
            icon("Outlined.DownloadForOffline") {
                path {
                    moveTo(12.0f, 2.0f)
                    curveTo(6.49f, 2.0f, 2.0f, 6.49f, 2.0f, 12.0f)
                    reflectiveCurveToRelative(4.49f, 10.0f, 10.0f, 10.0f)
                    reflectiveCurveToRelative(10.0f, -4.49f, 10.0f, -10.0f)
                    reflectiveCurveTo(17.51f, 2.0f, 12.0f, 2.0f)
                    close()
                    moveTo(12.0f, 20.0f)
                    curveToRelative(-4.41f, 0.0f, -8.0f, -3.59f, -8.0f, -8.0f)
                    reflectiveCurveToRelative(3.59f, -8.0f, 8.0f, -8.0f)
                    reflectiveCurveToRelative(8.0f, 3.59f, 8.0f, 8.0f)
                    reflectiveCurveTo(16.41f, 20.0f, 12.0f, 20.0f)
                    close()
                    moveTo(14.59f, 8.59f)
                    lineTo(16.0f, 10.0f)
                    lineToRelative(-4.0f, 4.0f)
                    lineToRelative(-4.0f, -4.0f)
                    lineToRelative(1.41f, -1.41f)
                    lineTo(11.0f, 10.17f)
                    verticalLineTo(6.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(4.17f)
                    lineTo(14.59f, 8.59f)
                    close()
                    moveTo(17.0f, 17.0f)
                    horizontalLineTo(7.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(10.0f)
                    verticalLineTo(17.0f)
                    close()
                }

            }
        }

        val ErrorOutline: ImageVector by lazy {
            icon("Outlined.ErrorOutline") {
                path {
                    moveTo(11.0f, 15.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(-2.0f)
                    close()
                    moveTo(11.0f, 7.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(6.0f)
                    horizontalLineToRelative(-2.0f)
                    lineTo(11.0f, 7.0f)
                    close()
                    moveTo(11.99f, 2.0f)
                    curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                    reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
                    curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
                    reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
                    close()
                    moveTo(12.0f, 20.0f)
                    curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
                    reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
                    reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
                    reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
                    close()
                }

            }
        }

        val Explore: ImageVector by lazy {
            icon("Outlined.Explore") {
                path {
                    moveTo(12.0f, 2.0f)
                    curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                    reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                    reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                    reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                    close()
                    moveTo(12.0f, 20.0f)
                    curveToRelative(-4.41f, 0.0f, -8.0f, -3.59f, -8.0f, -8.0f)
                    reflectiveCurveToRelative(3.59f, -8.0f, 8.0f, -8.0f)
                    reflectiveCurveToRelative(8.0f, 3.59f, 8.0f, 8.0f)
                    reflectiveCurveToRelative(-3.59f, 8.0f, -8.0f, 8.0f)
                    close()
                    moveTo(6.5f, 17.5f)
                    lineToRelative(7.51f, -3.49f)
                    lineTo(17.5f, 6.5f)
                    lineTo(9.99f, 9.99f)
                    lineTo(6.5f, 17.5f)
                    close()
                    moveTo(12.0f, 10.9f)
                    curveToRelative(0.61f, 0.0f, 1.1f, 0.49f, 1.1f, 1.1f)
                    reflectiveCurveToRelative(-0.49f, 1.1f, -1.1f, 1.1f)
                    reflectiveCurveToRelative(-1.1f, -0.49f, -1.1f, -1.1f)
                    reflectiveCurveToRelative(0.49f, -1.1f, 1.1f, -1.1f)
                    close()
                }

            }
        }

        val Extension: ImageVector by lazy {
            icon("Outlined.Extension") {
                path {
                    moveTo(10.5f, 4.5f)
                    curveToRelative(0.28f, 0.0f, 0.5f, 0.22f, 0.5f, 0.5f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(6.0f)
                    verticalLineToRelative(6.0f)
                    horizontalLineToRelative(2.0f)
                    curveToRelative(0.28f, 0.0f, 0.5f, 0.22f, 0.5f, 0.5f)
                    reflectiveCurveToRelative(-0.22f, 0.5f, -0.5f, 0.5f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(6.0f)
                    horizontalLineToRelative(-2.12f)
                    curveToRelative(-0.68f, -1.75f, -2.39f, -3.0f, -4.38f, -3.0f)
                    reflectiveCurveToRelative(-3.7f, 1.25f, -4.38f, 3.0f)
                    horizontalLineTo(4.0f)
                    verticalLineToRelative(-2.12f)
                    curveToRelative(1.75f, -0.68f, 3.0f, -2.39f, 3.0f, -4.38f)
                    curveToRelative(0.0f, -1.99f, -1.24f, -3.7f, -2.99f, -4.38f)
                    lineTo(4.0f, 7.0f)
                    horizontalLineToRelative(6.0f)
                    verticalLineTo(5.0f)
                    curveToRelative(0.0f, -0.28f, 0.22f, -0.5f, 0.5f, -0.5f)
                    moveToRelative(0.0f, -2.0f)
                    curveTo(9.12f, 2.5f, 8.0f, 3.62f, 8.0f, 5.0f)
                    horizontalLineTo(4.0f)
                    curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                    verticalLineToRelative(3.8f)
                    horizontalLineToRelative(0.29f)
                    curveToRelative(1.49f, 0.0f, 2.7f, 1.21f, 2.7f, 2.7f)
                    reflectiveCurveToRelative(-1.21f, 2.7f, -2.7f, 2.7f)
                    horizontalLineTo(2.0f)
                    verticalLineTo(20.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(3.8f)
                    verticalLineToRelative(-0.3f)
                    curveToRelative(0.0f, -1.49f, 1.21f, -2.7f, 2.7f, -2.7f)
                    reflectiveCurveToRelative(2.7f, 1.21f, 2.7f, 2.7f)
                    verticalLineToRelative(0.3f)
                    horizontalLineTo(17.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    verticalLineToRelative(-4.0f)
                    curveToRelative(1.38f, 0.0f, 2.5f, -1.12f, 2.5f, -2.5f)
                    reflectiveCurveTo(20.38f, 11.0f, 19.0f, 11.0f)
                    verticalLineTo(7.0f)
                    curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                    horizontalLineToRelative(-4.0f)
                    curveToRelative(0.0f, -1.38f, -1.12f, -2.5f, -2.5f, -2.5f)
                    close()
                }

            }
        }

        val HighQuality: ImageVector by lazy {
            icon("Outlined.HighQuality") {
                path {
                    moveTo(19.0f, 4.0f)
                    lineTo(5.0f, 4.0f)
                    curveToRelative(-1.11f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    verticalLineToRelative(12.0f)
                    curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(14.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    lineTo(21.0f, 6.0f)
                    curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                    close()
                    moveTo(19.0f, 18.0f)
                    lineTo(5.0f, 18.0f)
                    lineTo(5.0f, 6.0f)
                    horizontalLineToRelative(14.0f)
                    verticalLineToRelative(12.0f)
                    close()
                    moveTo(7.5f, 13.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(2.0f)
                    lineTo(11.0f, 15.0f)
                    lineTo(11.0f, 9.0f)
                    lineTo(9.5f, 9.0f)
                    verticalLineToRelative(2.5f)
                    horizontalLineToRelative(-2.0f)
                    lineTo(7.5f, 9.0f)
                    lineTo(6.0f, 9.0f)
                    verticalLineToRelative(6.0f)
                    horizontalLineToRelative(1.5f)
                    close()
                    moveTo(14.0f, 15.0f)
                    horizontalLineToRelative(0.75f)
                    verticalLineToRelative(1.5f)
                    horizontalLineToRelative(1.5f)
                    lineTo(16.25f, 15.0f)
                    lineTo(17.0f, 15.0f)
                    curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                    verticalLineToRelative(-4.0f)
                    curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                    horizontalLineToRelative(-3.0f)
                    curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                    verticalLineToRelative(4.0f)
                    curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                    close()
                    moveTo(14.5f, 10.5f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(3.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(-3.0f)
                    close()
                }

            }
        }

        val Home: ImageVector by lazy {
            icon("Outlined.Home") {
                path {
                    moveTo(12.0f, 5.69f)
                    lineToRelative(5.0f, 4.5f)
                    verticalLineTo(18.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(-6.0f)
                    horizontalLineTo(9.0f)
                    verticalLineToRelative(6.0f)
                    horizontalLineTo(7.0f)
                    verticalLineToRelative(-7.81f)
                    lineToRelative(5.0f, -4.5f)
                    moveTo(12.0f, 3.0f)
                    lineTo(2.0f, 12.0f)
                    horizontalLineToRelative(3.0f)
                    verticalLineToRelative(8.0f)
                    horizontalLineToRelative(6.0f)
                    verticalLineToRelative(-6.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(6.0f)
                    horizontalLineToRelative(6.0f)
                    verticalLineToRelative(-8.0f)
                    horizontalLineToRelative(3.0f)
                    lineTo(12.0f, 3.0f)
                    close()
                }

            }
        }

        val Hub: ImageVector by lazy {
            icon("Outlined.Hub") {
                path {
                    moveTo(21.0f, 6.5f)
                    curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
                    curveToRelative(0.0f, 0.07f, 0.0f, 0.14f, 0.01f, 0.21f)
                    lineToRelative(-2.03f, 0.68f)
                    curveToRelative(-0.64f, -1.21f, -1.82f, -2.09f, -3.22f, -2.32f)
                    verticalLineTo(5.91f)
                    curveTo(14.04f, 5.57f, 15.0f, 4.4f, 15.0f, 3.0f)
                    curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                    reflectiveCurveTo(9.0f, 1.34f, 9.0f, 3.0f)
                    curveToRelative(0.0f, 1.4f, 0.96f, 2.57f, 2.25f, 2.91f)
                    verticalLineToRelative(2.16f)
                    curveToRelative(-1.4f, 0.23f, -2.58f, 1.11f, -3.22f, 2.32f)
                    lineTo(5.99f, 9.71f)
                    curveTo(6.0f, 9.64f, 6.0f, 9.57f, 6.0f, 9.5f)
                    curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                    reflectiveCurveToRelative(-3.0f, 1.34f, -3.0f, 3.0f)
                    reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
                    curveToRelative(1.06f, 0.0f, 1.98f, -0.55f, 2.52f, -1.37f)
                    lineToRelative(2.03f, 0.68f)
                    curveToRelative(-0.2f, 1.29f, 0.17f, 2.66f, 1.09f, 3.69f)
                    lineToRelative(-1.41f, 1.77f)
                    curveTo(6.85f, 17.09f, 6.44f, 17.0f, 6.0f, 17.0f)
                    curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
                    reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
                    reflectiveCurveToRelative(3.0f, -1.34f, 3.0f, -3.0f)
                    curveToRelative(0.0f, -0.68f, -0.22f, -1.3f, -0.6f, -1.8f)
                    lineToRelative(1.41f, -1.77f)
                    curveToRelative(1.36f, 0.76f, 3.02f, 0.75f, 4.37f, 0.0f)
                    lineToRelative(1.41f, 1.77f)
                    curveTo(15.22f, 18.7f, 15.0f, 19.32f, 15.0f, 20.0f)
                    curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
                    reflectiveCurveToRelative(3.0f, -1.34f, 3.0f, -3.0f)
                    reflectiveCurveToRelative(-1.34f, -3.0f, -3.0f, -3.0f)
                    curveToRelative(-0.44f, 0.0f, -0.85f, 0.09f, -1.23f, 0.26f)
                    lineToRelative(-1.41f, -1.77f)
                    curveToRelative(0.93f, -1.04f, 1.29f, -2.4f, 1.09f, -3.69f)
                    lineToRelative(2.03f, -0.68f)
                    curveToRelative(0.53f, 0.82f, 1.46f, 1.37f, 2.52f, 1.37f)
                    curveToRelative(1.66f, 0.0f, 3.0f, -1.34f, 3.0f, -3.0f)
                    reflectiveCurveTo(22.66f, 6.5f, 21.0f, 6.5f)
                    close()
                    moveTo(3.0f, 10.5f)
                    curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                    curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                    reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
                    curveTo(4.0f, 10.05f, 3.55f, 10.5f, 3.0f, 10.5f)
                    close()
                    moveTo(6.0f, 21.0f)
                    curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                    curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                    reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
                    curveTo(7.0f, 20.55f, 6.55f, 21.0f, 6.0f, 21.0f)
                    close()
                    moveTo(11.0f, 3.0f)
                    curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                    reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
                    curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                    reflectiveCurveTo(11.0f, 3.55f, 11.0f, 3.0f)
                    close()
                    moveTo(12.0f, 15.0f)
                    curveToRelative(-1.38f, 0.0f, -2.5f, -1.12f, -2.5f, -2.5f)
                    curveToRelative(0.0f, -1.38f, 1.12f, -2.5f, 2.5f, -2.5f)
                    reflectiveCurveToRelative(2.5f, 1.12f, 2.5f, 2.5f)
                    curveTo(14.5f, 13.88f, 13.38f, 15.0f, 12.0f, 15.0f)
                    close()
                    moveTo(18.0f, 19.0f)
                    curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                    curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                    reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
                    curveTo(17.0f, 19.45f, 17.45f, 19.0f, 18.0f, 19.0f)
                    close()
                    moveTo(21.0f, 10.5f)
                    curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                    curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                    reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
                    curveTo(22.0f, 10.05f, 21.55f, 10.5f, 21.0f, 10.5f)
                    close()
                }

            }
        }

        val Inbox: ImageVector by lazy {
            icon("Outlined.Inbox") {
                path {
                    moveTo(19.0f, 3.0f)
                    lineTo(5.0f, 3.0f)
                    curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    verticalLineToRelative(14.0f)
                    curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(14.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    lineTo(21.0f, 5.0f)
                    curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                    close()
                    moveTo(19.0f, 19.0f)
                    lineTo(5.0f, 19.0f)
                    verticalLineToRelative(-3.0f)
                    horizontalLineToRelative(3.56f)
                    curveToRelative(0.69f, 1.19f, 1.97f, 2.0f, 3.45f, 2.0f)
                    reflectiveCurveToRelative(2.75f, -0.81f, 3.45f, -2.0f)
                    lineTo(19.0f, 16.0f)
                    verticalLineToRelative(3.0f)
                    close()
                    moveTo(19.0f, 14.0f)
                    horizontalLineToRelative(-4.99f)
                    curveToRelative(0.0f, 1.1f, -0.9f, 2.0f, -2.0f, 2.0f)
                    reflectiveCurveToRelative(-2.0f, -0.9f, -2.0f, -2.0f)
                    lineTo(5.0f, 14.0f)
                    lineTo(5.0f, 5.0f)
                    horizontalLineToRelative(14.0f)
                    verticalLineToRelative(9.0f)
                    close()
                }

            }
        }

        val Info: ImageVector by lazy {
            icon("Outlined.Info") {
                path {
                    moveTo(11.0f, 7.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(-2.0f)
                    close()
                    moveTo(11.0f, 11.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(6.0f)
                    horizontalLineToRelative(-2.0f)
                    close()
                    moveTo(12.0f, 2.0f)
                    curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                    reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                    reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                    reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                    close()
                    moveTo(12.0f, 20.0f)
                    curveToRelative(-4.41f, 0.0f, -8.0f, -3.59f, -8.0f, -8.0f)
                    reflectiveCurveToRelative(3.59f, -8.0f, 8.0f, -8.0f)
                    reflectiveCurveToRelative(8.0f, 3.59f, 8.0f, 8.0f)
                    reflectiveCurveToRelative(-3.59f, 8.0f, -8.0f, 8.0f)
                    close()
                }

            }
        }

        val Inventory2: ImageVector by lazy {
            icon("Outlined.Inventory2") {
                path {
                    moveTo(20.0f, 2.0f)
                    horizontalLineTo(4.0f)
                    curveTo(3.0f, 2.0f, 2.0f, 2.9f, 2.0f, 4.0f)
                    verticalLineToRelative(3.01f)
                    curveTo(2.0f, 7.73f, 2.43f, 8.35f, 3.0f, 8.7f)
                    verticalLineTo(20.0f)
                    curveToRelative(0.0f, 1.1f, 1.1f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(14.0f)
                    curveToRelative(0.9f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    verticalLineTo(8.7f)
                    curveToRelative(0.57f, -0.35f, 1.0f, -0.97f, 1.0f, -1.69f)
                    verticalLineTo(4.0f)
                    curveTo(22.0f, 2.9f, 21.0f, 2.0f, 20.0f, 2.0f)
                    close()
                    moveTo(19.0f, 20.0f)
                    horizontalLineTo(5.0f)
                    verticalLineTo(9.0f)
                    horizontalLineToRelative(14.0f)
                    verticalLineTo(20.0f)
                    close()
                    moveTo(20.0f, 7.0f)
                    horizontalLineTo(4.0f)
                    verticalLineTo(4.0f)
                    horizontalLineToRelative(16.0f)
                    verticalLineTo(7.0f)
                    close()
                }
                path {
                    moveTo(9.0f, 12.0f)
                    horizontalLineToRelative(6.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(-6.0f)
                    close()
                }

            }
        }

        val Link: ImageVector by lazy {
            icon("Outlined.Link") {
                path {
                    moveTo(17.0f, 7.0f)
                    horizontalLineToRelative(-4.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(4.0f)
                    curveToRelative(1.65f, 0.0f, 3.0f, 1.35f, 3.0f, 3.0f)
                    reflectiveCurveToRelative(-1.35f, 3.0f, -3.0f, 3.0f)
                    horizontalLineToRelative(-4.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(4.0f)
                    curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
                    reflectiveCurveToRelative(-2.24f, -5.0f, -5.0f, -5.0f)
                    close()
                    moveTo(11.0f, 15.0f)
                    lineTo(7.0f, 15.0f)
                    curveToRelative(-1.65f, 0.0f, -3.0f, -1.35f, -3.0f, -3.0f)
                    reflectiveCurveToRelative(1.35f, -3.0f, 3.0f, -3.0f)
                    horizontalLineToRelative(4.0f)
                    lineTo(11.0f, 7.0f)
                    lineTo(7.0f, 7.0f)
                    curveToRelative(-2.76f, 0.0f, -5.0f, 2.24f, -5.0f, 5.0f)
                    reflectiveCurveToRelative(2.24f, 5.0f, 5.0f, 5.0f)
                    horizontalLineToRelative(4.0f)
                    verticalLineToRelative(-2.0f)
                    close()
                    moveTo(8.0f, 11.0f)
                    horizontalLineToRelative(8.0f)
                    verticalLineToRelative(2.0f)
                    lineTo(8.0f, 13.0f)
                    close()
                }

            }
        }

        val PlayCircle: ImageVector by lazy {
            icon("Outlined.PlayCircle") {
                path {
                    moveTo(12.0f, 2.0f)
                    curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                    reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                    reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                    reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                    close()
                    moveTo(12.0f, 20.0f)
                    curveToRelative(-4.41f, 0.0f, -8.0f, -3.59f, -8.0f, -8.0f)
                    reflectiveCurveToRelative(3.59f, -8.0f, 8.0f, -8.0f)
                    reflectiveCurveToRelative(8.0f, 3.59f, 8.0f, 8.0f)
                    reflectiveCurveTo(16.41f, 20.0f, 12.0f, 20.0f)
                    close()
                    moveTo(9.5f, 16.5f)
                    lineToRelative(7.0f, -4.5f)
                    lineToRelative(-7.0f, -4.5f)
                    verticalLineTo(16.5f)
                    close()
                }

            }
        }

        val SearchOff: ImageVector by lazy {
            icon("Outlined.SearchOff") {
                path {
                    moveTo(15.5f, 14.0f)
                    horizontalLineToRelative(-0.79f)
                    lineToRelative(-0.28f, -0.27f)
                    curveTo(15.41f, 12.59f, 16.0f, 11.11f, 16.0f, 9.5f)
                    curveTo(16.0f, 5.91f, 13.09f, 3.0f, 9.5f, 3.0f)
                    curveTo(6.08f, 3.0f, 3.28f, 5.64f, 3.03f, 9.0f)
                    horizontalLineToRelative(2.02f)
                    curveTo(5.3f, 6.75f, 7.18f, 5.0f, 9.5f, 5.0f)
                    curveTo(11.99f, 5.0f, 14.0f, 7.01f, 14.0f, 9.5f)
                    reflectiveCurveTo(11.99f, 14.0f, 9.5f, 14.0f)
                    curveToRelative(-0.17f, 0.0f, -0.33f, -0.03f, -0.5f, -0.05f)
                    verticalLineToRelative(2.02f)
                    curveTo(9.17f, 15.99f, 9.33f, 16.0f, 9.5f, 16.0f)
                    curveToRelative(1.61f, 0.0f, 3.09f, -0.59f, 4.23f, -1.57f)
                    lineTo(14.0f, 14.71f)
                    verticalLineToRelative(0.79f)
                    lineToRelative(5.0f, 4.99f)
                    lineTo(20.49f, 19.0f)
                    lineTo(15.5f, 14.0f)
                    close()
                }
                path {
                    moveTo(6.47f, 10.82f)
                    lineToRelative(-2.47f, 2.47f)
                    lineToRelative(-2.47f, -2.47f)
                    lineToRelative(-0.71f, 0.71f)
                    lineToRelative(2.47f, 2.47f)
                    lineToRelative(-2.47f, 2.47f)
                    lineToRelative(0.71f, 0.71f)
                    lineToRelative(2.47f, -2.47f)
                    lineToRelative(2.47f, 2.47f)
                    lineToRelative(0.71f, -0.71f)
                    lineToRelative(-2.47f, -2.47f)
                    lineToRelative(2.47f, -2.47f)
                    close()
                }

            }
        }

        val Settings: ImageVector by lazy {
            icon("Outlined.Settings") {
                path {
                    moveTo(19.43f, 12.98f)
                    curveToRelative(0.04f, -0.32f, 0.07f, -0.64f, 0.07f, -0.98f)
                    curveToRelative(0.0f, -0.34f, -0.03f, -0.66f, -0.07f, -0.98f)
                    lineToRelative(2.11f, -1.65f)
                    curveToRelative(0.19f, -0.15f, 0.24f, -0.42f, 0.12f, -0.64f)
                    lineToRelative(-2.0f, -3.46f)
                    curveToRelative(-0.09f, -0.16f, -0.26f, -0.25f, -0.44f, -0.25f)
                    curveToRelative(-0.06f, 0.0f, -0.12f, 0.01f, -0.17f, 0.03f)
                    lineToRelative(-2.49f, 1.0f)
                    curveToRelative(-0.52f, -0.4f, -1.08f, -0.73f, -1.69f, -0.98f)
                    lineToRelative(-0.38f, -2.65f)
                    curveTo(14.46f, 2.18f, 14.25f, 2.0f, 14.0f, 2.0f)
                    horizontalLineToRelative(-4.0f)
                    curveToRelative(-0.25f, 0.0f, -0.46f, 0.18f, -0.49f, 0.42f)
                    lineToRelative(-0.38f, 2.65f)
                    curveToRelative(-0.61f, 0.25f, -1.17f, 0.59f, -1.69f, 0.98f)
                    lineToRelative(-2.49f, -1.0f)
                    curveToRelative(-0.06f, -0.02f, -0.12f, -0.03f, -0.18f, -0.03f)
                    curveToRelative(-0.17f, 0.0f, -0.34f, 0.09f, -0.43f, 0.25f)
                    lineToRelative(-2.0f, 3.46f)
                    curveToRelative(-0.13f, 0.22f, -0.07f, 0.49f, 0.12f, 0.64f)
                    lineToRelative(2.11f, 1.65f)
                    curveToRelative(-0.04f, 0.32f, -0.07f, 0.65f, -0.07f, 0.98f)
                    curveToRelative(0.0f, 0.33f, 0.03f, 0.66f, 0.07f, 0.98f)
                    lineToRelative(-2.11f, 1.65f)
                    curveToRelative(-0.19f, 0.15f, -0.24f, 0.42f, -0.12f, 0.64f)
                    lineToRelative(2.0f, 3.46f)
                    curveToRelative(0.09f, 0.16f, 0.26f, 0.25f, 0.44f, 0.25f)
                    curveToRelative(0.06f, 0.0f, 0.12f, -0.01f, 0.17f, -0.03f)
                    lineToRelative(2.49f, -1.0f)
                    curveToRelative(0.52f, 0.4f, 1.08f, 0.73f, 1.69f, 0.98f)
                    lineToRelative(0.38f, 2.65f)
                    curveToRelative(0.03f, 0.24f, 0.24f, 0.42f, 0.49f, 0.42f)
                    horizontalLineToRelative(4.0f)
                    curveToRelative(0.25f, 0.0f, 0.46f, -0.18f, 0.49f, -0.42f)
                    lineToRelative(0.38f, -2.65f)
                    curveToRelative(0.61f, -0.25f, 1.17f, -0.59f, 1.69f, -0.98f)
                    lineToRelative(2.49f, 1.0f)
                    curveToRelative(0.06f, 0.02f, 0.12f, 0.03f, 0.18f, 0.03f)
                    curveToRelative(0.17f, 0.0f, 0.34f, -0.09f, 0.43f, -0.25f)
                    lineToRelative(2.0f, -3.46f)
                    curveToRelative(0.12f, -0.22f, 0.07f, -0.49f, -0.12f, -0.64f)
                    lineToRelative(-2.11f, -1.65f)
                    close()
                    moveTo(17.45f, 11.27f)
                    curveToRelative(0.04f, 0.31f, 0.05f, 0.52f, 0.05f, 0.73f)
                    curveToRelative(0.0f, 0.21f, -0.02f, 0.43f, -0.05f, 0.73f)
                    lineToRelative(-0.14f, 1.13f)
                    lineToRelative(0.89f, 0.7f)
                    lineToRelative(1.08f, 0.84f)
                    lineToRelative(-0.7f, 1.21f)
                    lineToRelative(-1.27f, -0.51f)
                    lineToRelative(-1.04f, -0.42f)
                    lineToRelative(-0.9f, 0.68f)
                    curveToRelative(-0.43f, 0.32f, -0.84f, 0.56f, -1.25f, 0.73f)
                    lineToRelative(-1.06f, 0.43f)
                    lineToRelative(-0.16f, 1.13f)
                    lineToRelative(-0.2f, 1.35f)
                    horizontalLineToRelative(-1.4f)
                    lineToRelative(-0.19f, -1.35f)
                    lineToRelative(-0.16f, -1.13f)
                    lineToRelative(-1.06f, -0.43f)
                    curveToRelative(-0.43f, -0.18f, -0.83f, -0.41f, -1.23f, -0.71f)
                    lineToRelative(-0.91f, -0.7f)
                    lineToRelative(-1.06f, 0.43f)
                    lineToRelative(-1.27f, 0.51f)
                    lineToRelative(-0.7f, -1.21f)
                    lineToRelative(1.08f, -0.84f)
                    lineToRelative(0.89f, -0.7f)
                    lineToRelative(-0.14f, -1.13f)
                    curveToRelative(-0.03f, -0.31f, -0.05f, -0.54f, -0.05f, -0.74f)
                    reflectiveCurveToRelative(0.02f, -0.43f, 0.05f, -0.73f)
                    lineToRelative(0.14f, -1.13f)
                    lineToRelative(-0.89f, -0.7f)
                    lineToRelative(-1.08f, -0.84f)
                    lineToRelative(0.7f, -1.21f)
                    lineToRelative(1.27f, 0.51f)
                    lineToRelative(1.04f, 0.42f)
                    lineToRelative(0.9f, -0.68f)
                    curveToRelative(0.43f, -0.32f, 0.84f, -0.56f, 1.25f, -0.73f)
                    lineToRelative(1.06f, -0.43f)
                    lineToRelative(0.16f, -1.13f)
                    lineToRelative(0.2f, -1.35f)
                    horizontalLineToRelative(1.39f)
                    lineToRelative(0.19f, 1.35f)
                    lineToRelative(0.16f, 1.13f)
                    lineToRelative(1.06f, 0.43f)
                    curveToRelative(0.43f, 0.18f, 0.83f, 0.41f, 1.23f, 0.71f)
                    lineToRelative(0.91f, 0.7f)
                    lineToRelative(1.06f, -0.43f)
                    lineToRelative(1.27f, -0.51f)
                    lineToRelative(0.7f, 1.21f)
                    lineToRelative(-1.07f, 0.85f)
                    lineToRelative(-0.89f, 0.7f)
                    lineToRelative(0.14f, 1.13f)
                    close()
                    moveTo(12.0f, 8.0f)
                    curveToRelative(-2.21f, 0.0f, -4.0f, 1.79f, -4.0f, 4.0f)
                    reflectiveCurveToRelative(1.79f, 4.0f, 4.0f, 4.0f)
                    reflectiveCurveToRelative(4.0f, -1.79f, 4.0f, -4.0f)
                    reflectiveCurveToRelative(-1.79f, -4.0f, -4.0f, -4.0f)
                    close()
                    moveTo(12.0f, 14.0f)
                    curveToRelative(-1.1f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                    reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                    reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                    reflectiveCurveToRelative(-0.9f, 2.0f, -2.0f, 2.0f)
                    close()
                }

            }
        }

        val Sync: ImageVector by lazy {
            icon("Outlined.Sync") {
                path {
                    moveTo(12.0f, 4.0f)
                    lineTo(12.0f, 1.0f)
                    lineTo(8.0f, 5.0f)
                    lineToRelative(4.0f, 4.0f)
                    lineTo(12.0f, 6.0f)
                    curveToRelative(3.31f, 0.0f, 6.0f, 2.69f, 6.0f, 6.0f)
                    curveToRelative(0.0f, 1.01f, -0.25f, 1.97f, -0.7f, 2.8f)
                    lineToRelative(1.46f, 1.46f)
                    curveTo(19.54f, 15.03f, 20.0f, 13.57f, 20.0f, 12.0f)
                    curveToRelative(0.0f, -4.42f, -3.58f, -8.0f, -8.0f, -8.0f)
                    close()
                    moveTo(12.0f, 18.0f)
                    curveToRelative(-3.31f, 0.0f, -6.0f, -2.69f, -6.0f, -6.0f)
                    curveToRelative(0.0f, -1.01f, 0.25f, -1.97f, 0.7f, -2.8f)
                    lineTo(5.24f, 7.74f)
                    curveTo(4.46f, 8.97f, 4.0f, 10.43f, 4.0f, 12.0f)
                    curveToRelative(0.0f, 4.42f, 3.58f, 8.0f, 8.0f, 8.0f)
                    verticalLineToRelative(3.0f)
                    lineToRelative(4.0f, -4.0f)
                    lineToRelative(-4.0f, -4.0f)
                    verticalLineToRelative(3.0f)
                    close()
                }

            }
        }

        val VerifiedUser: ImageVector by lazy {
            icon("Outlined.VerifiedUser") {
                path {
                    moveTo(12.0f, 1.0f)
                    lineTo(3.0f, 5.0f)
                    verticalLineToRelative(6.0f)
                    curveToRelative(0.0f, 5.55f, 3.84f, 10.74f, 9.0f, 12.0f)
                    curveToRelative(5.16f, -1.26f, 9.0f, -6.45f, 9.0f, -12.0f)
                    lineTo(21.0f, 5.0f)
                    lineToRelative(-9.0f, -4.0f)
                    close()
                    moveTo(19.0f, 11.0f)
                    curveToRelative(0.0f, 4.52f, -2.98f, 8.69f, -7.0f, 9.93f)
                    curveToRelative(-4.02f, -1.24f, -7.0f, -5.41f, -7.0f, -9.93f)
                    lineTo(5.0f, 6.3f)
                    lineToRelative(7.0f, -3.11f)
                    lineToRelative(7.0f, 3.11f)
                    lineTo(19.0f, 11.0f)
                    close()
                    moveTo(7.41f, 11.59f)
                    lineTo(6.0f, 13.0f)
                    lineToRelative(4.0f, 4.0f)
                    lineToRelative(8.0f, -8.0f)
                    lineToRelative(-1.41f, -1.42f)
                    lineTo(10.0f, 14.17f)
                    close()
                }

            }
        }

        val VideoLibrary: ImageVector by lazy {
            icon("Outlined.VideoLibrary") {
                path {
                    moveTo(4.0f, 6.0f)
                    lineTo(2.0f, 6.0f)
                    verticalLineToRelative(14.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(14.0f)
                    verticalLineToRelative(-2.0f)
                    lineTo(4.0f, 20.0f)
                    lineTo(4.0f, 6.0f)
                    close()
                    moveTo(20.0f, 2.0f)
                    lineTo(8.0f, 2.0f)
                    curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    verticalLineToRelative(12.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(12.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    lineTo(22.0f, 4.0f)
                    curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                    close()
                    moveTo(20.0f, 16.0f)
                    lineTo(8.0f, 16.0f)
                    lineTo(8.0f, 4.0f)
                    horizontalLineToRelative(12.0f)
                    verticalLineToRelative(12.0f)
                    close()
                    moveTo(12.0f, 5.5f)
                    verticalLineToRelative(9.0f)
                    lineToRelative(6.0f, -4.5f)
                    close()
                }

            }
        }
    }

    object AutoMirrored {
        object Filled {
            val ArrowBack: ImageVector by lazy {
                icon("AutoMirrored.Filled.ArrowBack", autoMirror = true) {
                    path {
                        moveTo(20.0f, 11.0f)
                        horizontalLineTo(7.83f)
                        lineToRelative(5.59f, -5.59f)
                        lineTo(12.0f, 4.0f)
                        lineToRelative(-8.0f, 8.0f)
                        lineToRelative(8.0f, 8.0f)
                        lineToRelative(1.41f, -1.41f)
                        lineTo(7.83f, 13.0f)
                        horizontalLineTo(20.0f)
                        verticalLineToRelative(-2.0f)
                        close()
                    }

                }
            }

            val PlaylistPlay: ImageVector by lazy {
                icon("AutoMirrored.Filled.PlaylistPlay", autoMirror = true) {
                    path {
                        moveTo(3.0f, 10.0f)
                        horizontalLineToRelative(11.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineToRelative(-11.0f)
                        close()
                    }
                    path {
                        moveTo(3.0f, 6.0f)
                        horizontalLineToRelative(11.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineToRelative(-11.0f)
                        close()
                    }
                    path {
                        moveTo(3.0f, 14.0f)
                        horizontalLineToRelative(7.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineToRelative(-7.0f)
                        close()
                    }
                    path {
                        moveTo(16.0f, 13.0f)
                        lineToRelative(0.0f, 8.0f)
                        lineToRelative(6.0f, -4.0f)
                        close()
                    }

                }
            }
        }
    }
}

/** Scope for one icon's paths: [path] adds a filled sub-path (optionally translucent). */
class IconScope internal constructor(private val builder: ImageVector.Builder) {
    fun path(fillAlpha: Float = 1f, block: PathBuilder.() -> Unit) {
        builder.path(
            fill = SolidColor(Color.Black),
            fillAlpha = fillAlpha,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.NonZero,
            pathBuilder = block,
        )
    }
}

private inline fun icon(name: String, autoMirror: Boolean = false, block: IconScope.() -> Unit): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    )
    IconScope(builder).block()
    return builder.build()
}
