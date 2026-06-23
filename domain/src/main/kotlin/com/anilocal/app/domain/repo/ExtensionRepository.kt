package com.anilocal.app.domain.repo

import com.anilocal.app.domain.model.ExtensionEntry
import java.io.File

/**
 * Browses configured extension repos (each an Aniyomi-style `index.min.json`) and downloads
 * extension APKs for the system package installer. Installation itself is launched from the UI
 * (needs an Activity + FileProvider); discovery + download live here. The dynamic loader picks up a
 * newly-installed extension on the next [com.anilocal.app.domain.source.SourceRegistry] refresh.
 */
interface ExtensionRepository {
    /** Every extension available across the configured repos, de-duplicated by package. */
    suspend fun available(): List<ExtensionEntry>

    /** Download [entry]'s APK into app cache and return the file, ready to hand to the installer. */
    suspend fun downloadApk(entry: ExtensionEntry): File
}
