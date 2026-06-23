package com.anilocal.app.domain.model

/** An installable extension as listed in a repo's `index.min.json`. */
data class ExtensionEntry(
    val name: String,
    val pkg: String,
    /** Absolute URL of the extension APK, derived from the repo base + the index's `apk` filename. */
    val apkUrl: String,
    val lang: String,
    val versionName: String,
    val isNsfw: Boolean,
    /** Human names of the source(s) the extension provides (for display). */
    val sourceNames: List<String>,
)
