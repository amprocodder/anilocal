package com.anilocal.app.data.metadata.extension

import android.content.Context
import android.util.Log
import com.anilocal.app.data.cache.JsonCache
import com.anilocal.app.domain.model.ExtensionEntry
import com.anilocal.app.domain.repo.ExtensionRepository
import com.anilocal.app.domain.repo.SettingsRepository
import com.anilocal.app.data.source.AutoSourceSelector
import com.anilocal.app.domain.source.SourceRegistry
import com.anilocal.app.extensions.loader.AnimeExtensionLoader
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lists extensions from each configured repo's `index.min.json` (pre-seeded with the yuzono repo)
 * and downloads their APKs to app cache. Each repo's parsed index is cached ([JsonCache]) so the
 * Browse list still renders offline / when a repo host is down — installs need the network, but
 * discovery doesn't. Repo/network failures with no cache degrade to an empty list, matching the
 * app's silent-degrade convention.
 */
@Singleton
class ExtensionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ExtensionRepoApi,
    private val okHttp: OkHttpClient,
    private val settings: SettingsRepository,
    private val cache: JsonCache,
    private val registry: SourceRegistry,
    private val selector: AutoSourceSelector,
) : ExtensionRepository {

    override suspend fun available(): List<ExtensionEntry> = withContext(Dispatchers.IO) {
        settings.extensionRepoBaseUrls.first()
            .flatMap { base ->
                val root = base.trimEnd('/')
                runCatching {
                    cache.cached("extrepo:$root", ENTRIES, REPO_TTL_MS, preferStaleOverEmpty = true) {
                        api.index("$root/index.min.json").map { it.toEntry(root) }
                    }
                }.getOrDefault(emptyList())
            }
            .distinctBy { it.pkg }
            .sortedBy { it.name.lowercase() }
    }

    override suspend fun downloadApk(entry: ExtensionEntry): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "extensions").apply { mkdirs() }
        val file = File(dir, entry.apkUrl.substringAfterLast('/'))
        downloadTo(entry.apkUrl, file)
        file
    }

    override suspend fun privateInstall(entry: ExtensionEntry, requireVerified: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val dir = AnimeExtensionLoader.privateDir(context).apply { mkdirs() }
            val target = File(dir, entry.pkg + AnimeExtensionLoader.PRIVATE_APK_SUFFIX)
            val staging = File(dir, "${entry.pkg}.staging")
            try {
                runCatching { downloadTo(entry.apkUrl, staging) }.getOrElse {
                    Log.w(TAG, "privateInstall ${entry.pkg}: download failed: ${it.message}")
                    return@withContext false
                }

                val id = ExtensionSignatures.identifyApk(pm, staging.absolutePath)
                    ?: run { Log.w(TAG, "privateInstall ${entry.pkg}: not a readable APK"); return@withContext false }
                // The APK must actually BE the advertised package — a repo can't redirect an entry at
                // some other (e.g. malicious) apk and have it load under the trusted name.
                if (id.pkg != entry.pkg) {
                    Log.w(TAG, "privateInstall ${entry.pkg}: apk declares ${id.pkg}"); return@withContext false
                }
                if (id.signatures.isEmpty()) {
                    Log.w(TAG, "privateInstall ${entry.pkg}: unsigned APK"); return@withContext false
                }

                val fingerprint = repoFingerprint(entry)
                when {
                    fingerprint != null ->
                        if (id.signatures.none { it.equals(fingerprint, ignoreCase = true) }) {
                            Log.w(TAG, "privateInstall ${entry.pkg}: signing cert doesn't match repo fingerprint")
                            return@withContext false
                        }
                    // Auto-install must never run code from a repo we can't verify.
                    requireVerified -> {
                        Log.w(TAG, "privateInstall ${entry.pkg}: repo has no fingerprint; refusing auto-install")
                        return@withContext false
                    }
                    // else: manual install from a repo without repo.json — allowed (user-initiated).
                }

                // Update guard: never downgrade, never accept a changed signer, vs an existing private copy.
                if (target.exists()) {
                    ExtensionSignatures.identifyApk(pm, target.absolutePath)?.let { cur ->
                        if (id.versionCode < cur.versionCode) {
                            Log.w(TAG, "privateInstall ${entry.pkg}: downgrade blocked"); return@withContext false
                        }
                        if (cur.signatures.isNotEmpty() && !id.signatures.containsAll(cur.signatures)) {
                            Log.w(TAG, "privateInstall ${entry.pkg}: signer changed, blocked"); return@withContext false
                        }
                    }
                }

                // Commit: read-only APK in app-private storage. Android 14+ (targetSdk 34+) refuses to
                // class-load a writable dex, so setReadOnly() is mandatory, not hygiene.
                if (target.exists()) target.delete()
                if (!staging.renameTo(target)) staging.copyTo(target, overwrite = true)
                target.setReadOnly()
            } finally {
                staging.delete()
            }
            registry.refresh()
            true
        }

    override suspend fun privateUninstall(pkg: String) {
        withContext(Dispatchers.IO) {
            val file = File(AnimeExtensionLoader.privateDir(context), pkg + AnimeExtensionLoader.PRIVATE_APK_SUFFIX)
            if (file.delete()) registry.refresh()
        }
    }

    override suspend fun installedPackages(): Set<String> = withContext(Dispatchers.IO) {
        AnimeExtensionLoader.installedPackageNames(context) + privatePackageNames()
    }

    override suspend fun privatelyInstalled(): Set<String> = withContext(Dispatchers.IO) { privatePackageNames() }

    override suspend fun installRecommended(target: Int): Int = withContext(Dispatchers.IO) {
        val evicted = settings.evictedSources.first()
        val index = runCatching { available() }.getOrDefault(emptyList())
        val installedNow = installedPackages()
        // Ranked recommended entries present in the repos, minus any we auto-evicted as dead (so
        // provisioning doesn't just keep re-installing a source eviction already judged a loser).
        val seedEntries = RecommendedSources.RANKED
            .filter { it !in evicted }
            .mapNotNull { pkg -> index.firstOrNull { it.pkg == pkg } }
        val alreadySeeded = seedEntries.count { it.pkg in installedNow }
        var installed = 0
        val autoSet = settings.autoInstalledSources.first().toMutableSet()
        for (entry in seedEntries) {
            if (alreadySeeded + installed >= target) break
            if (entry.pkg in installedNow) continue
            // requireVerified: auto-install must never run code from a repo without a signing fingerprint.
            if (runCatching { privateInstall(entry, requireVerified = true) }.getOrDefault(false)) {
                installed++
                autoSet += entry.pkg   // mark as app-installed → eligible for later auto-eviction
                Log.i(TAG, "installRecommended: added ${entry.pkg}")
            }
        }
        if (installed > 0) settings.setAutoInstalledSources(autoSet)
        installed
    }

    override suspend fun pruneLosers(): Int = withContext(Dispatchers.IO) {
        val autoInstalled = settings.autoInstalledSources.first()
        if (autoInstalled.isEmpty()) return@withContext 0

        val sources = registry.sources.value
        val extensionPkgs = sources.mapNotNull { it.info.pkg }.toSet()
        if (extensionPkgs.size <= MIN_KEPT_PACKAGES) return@withContext 0   // keep a floor to race against

        val pinned = runCatching { selector.pinnedSourceIds() }.getOrDefault(emptySet())
        val byPkg = sources.filter { it.info.pkg != null }.groupBy { it.info.pkg!! }

        // Candidate losers: app-installed packages, currently loaded, NOT pinned as any title's best,
        // with enough recorded attempts and a poor aggregate success rate. (Cancelled race losers
        // record nothing, so failures here mean the source actually failed to resolve, not merely
        // that it was slower — so this targets broken/dead sources, not working-but-slow ones.)
        val losers = mutableListOf<Pair<String, Double>>()
        for (pkg in autoInstalled) {
            val pkgSources = byPkg[pkg] ?: continue                      // not currently loaded → skip
            if (pkgSources.any { it.info.id in pinned }) continue        // best for some title → keep
            var succ = 0
            var fail = 0
            for (s in pkgSources) {
                val h = selector.healthOf(s.info.id) ?: continue
                succ += h.successes
                fail += h.failures
            }
            val attempts = succ + fail
            if (attempts < MIN_ATTEMPTS) continue
            val rate = succ.toDouble() / attempts
            if (rate < LOSER_SUCCESS_RATE) losers += pkg to rate
        }
        if (losers.isEmpty()) return@withContext 0

        // Never drop below the floor; remove the worst first.
        val maxEvictable = (extensionPkgs.size - MIN_KEPT_PACKAGES).coerceAtLeast(0)
        val toEvict = losers.sortedBy { it.second }.take(maxEvictable)
        if (toEvict.isEmpty()) return@withContext 0

        val nowEvicted = settings.evictedSources.first().toMutableSet()
        val nowAuto = autoInstalled.toMutableSet()
        var evicted = 0
        for ((pkg, rate) in toEvict) {
            val file = File(AnimeExtensionLoader.privateDir(context), pkg + AnimeExtensionLoader.PRIVATE_APK_SUFFIX)
            if (file.delete()) {
                nowEvicted += pkg
                nowAuto -= pkg
                evicted++
                Log.i(TAG, "pruneLosers: evicted $pkg (successRate=${"%.2f".format(rate)})")
            }
        }
        if (evicted > 0) {
            settings.setEvictedSources(nowEvicted)
            settings.setAutoInstalledSources(nowAuto)
            registry.refresh()   // one rescan for the whole batch
        }
        evicted
    }

    override suspend fun recommendedNotInstalled(): List<ExtensionEntry> = withContext(Dispatchers.IO) {
        val index = runCatching { available() }.getOrDefault(emptyList())
        val installedNow = installedPackages()
        RecommendedSources.RANKED
            .mapNotNull { pkg -> index.firstOrNull { it.pkg == pkg } }
            .filter { it.pkg !in installedNow }
    }

    /** Private extensions are stored as `<pkg>.apk`, so the file names ARE the package names. */
    private fun privatePackageNames(): Set<String> =
        AnimeExtensionLoader.privateDir(context)
            .listFiles { f -> f.isFile && f.name.endsWith(AnimeExtensionLoader.PRIVATE_APK_SUFFIX) }
            ?.map { it.name.removeSuffix(AnimeExtensionLoader.PRIVATE_APK_SUFFIX) }
            ?.toSet()
            ?: emptySet()

    /** The repo's published signing-cert SHA-256, or null if the repo has no `repo.json`/fingerprint. */
    private suspend fun repoFingerprint(entry: ExtensionEntry): String? = runCatching {
        val root = entry.repoRoot.ifBlank { entry.apkUrl.substringBefore("/apk/") }.trimEnd('/')
        if (root.isBlank()) return null
        cache.cached("extmeta:$root", META_TYPE, REPO_TTL_MS) { api.repoMeta("$root/repo.json") }
            .meta?.signingKeyFingerprint?.lowercase()?.replace(":", "")?.trim()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /** Stream [url] to [dest] via a temp file, renaming only on success so an interrupted transfer
     *  never leaves a truncated APK behind. */
    private fun downloadTo(url: String, dest: File) {
        val tmp = File(dest.parentFile, "${dest.name}.part")
        try {
            okHttp.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) error("download failed (HTTP ${resp.code})")
                tmp.outputStream().use { out -> resp.body.byteStream().copyTo(out) }
            }
            dest.delete()
            if (!tmp.renameTo(dest)) error("couldn't finalize ${dest.name}")
        } finally {
            tmp.delete()
        }
    }

    private companion object {
        val ENTRIES: java.lang.reflect.Type =
            Types.newParameterizedType(List::class.java, ExtensionEntry::class.java)
        val META_TYPE: java.lang.reflect.Type = RepoMetaDto::class.java
        const val REPO_TTL_MS = 60L * 60 * 1000   // repos publish updates at most a few times a day
        const val TAG = "AniLocalExtensions"

        // Auto-eviction thresholds.
        const val MIN_ATTEMPTS = 6              // recorded resolves before a package can be judged
        const val LOSER_SUCCESS_RATE = 0.15     // below this success rate (after MIN_ATTEMPTS) = a loser
        const val MIN_KEPT_PACKAGES = 2         // never evict below this many extension packages
    }

    private fun RepoEntryDto.toEntry(repoRoot: String) = ExtensionEntry(
        name = name.substringAfter("Aniyomi: ").ifBlank { name },
        pkg = pkg,
        apkUrl = "$repoRoot/apk/$apk",
        lang = lang,
        versionName = version,
        isNsfw = nsfw == 1,
        sourceNames = sources?.map { it.name } ?: emptyList(),
        repoRoot = repoRoot,
    )
}
