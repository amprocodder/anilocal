package com.anilocal.app.ui.common

import java.util.Locale

/**
 * AniList reports scores as 0–100; render the site-style "★ 8.5". Locale is pinned so a comma
 * decimal separator (de/fr/…) can't change the label.
 */
fun scoreLabel(score: Int): String = "★ %.1f".format(Locale.US, score / 10f)
