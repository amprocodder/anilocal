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

    /**
     * Silently install [entry] as a PRIVATE extension — download → verify its signing cert against
     * the repo's `signingKeyFingerprint` → copy read-only into app-private storage → rescan sources —
     * with no system-installer tap. Returns true on success, false if verification/download failed.
     * When [requireVerified] is true a repo that publishes no fingerprint is rejected outright (used
     * for automatic installs, which must never run unverified downloaded code).
     */
    suspend fun privateInstall(entry: ExtensionEntry, requireVerified: Boolean = false): Boolean

    /** Remove a privately-installed extension by package name and rescan. No-op for shared installs. */
    suspend fun privateUninstall(pkg: String)

    /** Package names of every currently-active extension (system-installed or private). */
    suspend fun installedPackages(): Set<String>

    /** Package names the app installed privately (these are the ones it can uninstall in-app). */
    suspend fun privatelyInstalled(): Set<String>

    /**
     * Bootstrap "Auto (best source)" by silently installing community-recommended sources (verified
     * only) until [target] of them are present, skipping any already installed. Best-effort and
     * idempotent — safe to call repeatedly (it tops up, never duplicates). Returns how many it newly
     * installed this call.
     */
    suspend fun installRecommended(target: Int = 4): Int

    /** Recommended-source entries present in the configured repos that aren't installed yet — for the
     *  "Install recommended sources" affordance to know whether there's anything left to add. */
    suspend fun recommendedNotInstalled(): List<ExtensionEntry>

    /**
     * Auto-evict dead/losing sources: uninstall app-installed packages whose sources have a proven
     * poor track record (low success rate after enough attempts) and aren't the pinned best for any
     * title, keeping a floor so Auto always has candidates. Never touches user-installed extensions;
     * evicted packages are remembered so provisioning won't re-add them. Returns how many it removed.
     */
    suspend fun pruneLosers(): Int
}
