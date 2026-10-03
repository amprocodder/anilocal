package com.anilocal.app.extensions.loader

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.anilocal.app.extensions.AniyomiRuntime
import com.anilocal.app.extensions.AniyomiSourceAdapter
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import java.io.File
import com.anilocal.app.domain.source.AnimeSource as DomainAnimeSource

/**
 * Discovers Aniyomi anime-extension APKs and adapts their sources onto the domain seam. Ported from
 * aniyomi's AnimeExtensionLoader (the discovery/metadata/classloader mechanism). Loads from two
 * places, deduped by package (higher versionCode wins; a system-install ties in its favour):
 *   1. **Installed (shared)** packages the user sideloaded via the system installer.
 *   2. **Private** APKs in [privateDir] that the app installed itself (Phase B) — class-loaded
 *      straight off the file with no system-installer tap. Only verified APKs are ever written there
 *      (the install-time trust gate in :data checks the signing cert against the repo fingerprint),
 *      so the loader trusts the directory's contents the same way it trusts a user's manual sideload.
 */
object AnimeExtensionLoader {

    private const val EXTENSION_FEATURE = "tachiyomi.animeextension"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.animeextension.class"
    private const val LIB_VERSION_MIN = 12.0
    private const val LIB_VERSION_MAX = 16.0

    private const val PRIVATE_DIR = "exts"
    /** File suffix for a privately-installed extension APK (kept read-only in app storage). */
    const val PRIVATE_APK_SUFFIX = ".apk"

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA

    /** App-private directory holding self-installed extension APKs. Shared by the loader (reads) and
     *  the :data installer (writes) so both agree on where private extensions live. */
    fun privateDir(context: Context): File = File(context.filesDir, PRIVATE_DIR)

    /** Package names of installed (shared) anime extensions — for the browse UI's installed state. */
    fun installedPackageNames(context: Context): Set<String> =
        installedPackages(context.packageManager).map { it.packageName }.toSet()

    /**
     * All extensions' sources (installed + private), each wrapped in an [AniyomiSourceAdapter]. Seeds
     * the Injekt runtime first so a loaded `AnimeHttpSource` can construct. Safe to call off the main
     * thread.
     */
    fun loadSources(context: Context): List<DomainAnimeSource> {
        AniyomiRuntime.seed(context.applicationContext as Application)
        val pm = context.packageManager
        return dedupeByPackage(installedPackages(pm), privatePackages(context, pm))
            .flatMap { runCatching { loadPackage(context, it) }.getOrDefault(emptyList()) }
    }

    private fun installedPackages(pm: PackageManager): List<PackageInfo> {
        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PACKAGE_FLAGS)
        }
        return installed.filter { it.isAnimeExtension() }
    }

    /** Parse each private APK file's manifest via [PackageManager.getPackageArchiveInfo], patching the
     *  sourceDir/publicSourceDir the classloader needs (getPackageArchiveInfo leaves them null on
     *  API 33+). A file that no longer parses / isn't an anime extension is skipped silently. */
    private fun privatePackages(context: Context, pm: PackageManager): List<PackageInfo> {
        val files = privateDir(context)
            .listFiles { f -> f.isFile && f.name.endsWith(PRIVATE_APK_SUFFIX) }
            ?: return emptyList()
        return files.mapNotNull { f ->
            runCatching {
                @Suppress("DEPRECATION")
                val info = pm.getPackageArchiveInfo(f.absolutePath, PACKAGE_FLAGS) ?: return@mapNotNull null
                val appInfo = info.applicationInfo ?: return@mapNotNull null
                appInfo.sourceDir = f.absolutePath
                appInfo.publicSourceDir = f.absolutePath
                info.takeIf { it.isAnimeExtension() }
            }.getOrNull()
        }
    }

    /** Merge shared + private by package name: prefer the higher versionCode; a system install wins a
     *  tie (matches Mihon's selectExtensionPackage — the shared copy is the one the user explicitly
     *  approved via the OS installer). */
    private fun dedupeByPackage(installed: List<PackageInfo>, private: List<PackageInfo>): List<PackageInfo> {
        val byPkg = LinkedHashMap<String, PackageInfo>()
        installed.forEach { byPkg[it.packageName] = it }
        private.forEach { p ->
            val existing = byPkg[p.packageName]
            if (existing == null || p.versionCodeCompat() > existing.versionCodeCompat()) byPkg[p.packageName] = p
        }
        return byPkg.values.toList()
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.versionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    private fun PackageInfo.isAnimeExtension(): Boolean =
        reqFeatures?.any { it.name == EXTENSION_FEATURE } == true

    private fun loadPackage(context: Context, pkgInfo: PackageInfo): List<DomainAnimeSource> {
        val appInfo = pkgInfo.applicationInfo ?: return emptyList()

        // Lib version is the major.minor of the extension's versionName (e.g. "16.0.3" -> 16.0).
        val libVersion = pkgInfo.versionName?.substringBeforeLast('.')?.toDoubleOrNull() ?: return emptyList()
        if (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) return emptyList()

        val classNames = appInfo.metaData?.getString(METADATA_SOURCE_CLASS) ?: return emptyList()
        val classLoader = ChildFirstPathClassLoader(appInfo.sourceDir, null, context.classLoader)

        // Tag every adapted source with its package so install/uninstall/eviction can act per-package.
        return classNames.split(";").flatMap { raw ->
            val className = raw.trim().let { if (it.startsWith(".")) pkgInfo.packageName + it else it }
            instantiate(className, classLoader, appInfo.sourceDir, context)
        }.map { AniyomiSourceAdapter(it, pkgInfo.packageName) }
    }

    private fun instantiate(
        className: String,
        classLoader: ClassLoader,
        sourceDir: String,
        context: Context,
    ): List<AnimeSource> = try {
        sourcesOf(Class.forName(className, false, classLoader).getDeclaredConstructor().newInstance())
    } catch (e: LinkageError) {
        // Child-first hit a linkage conflict (a bundled lib clashing with the host) — retry parent-first.
        runCatching {
            val fallback = PathClassLoader(sourceDir, null, context.classLoader)
            sourcesOf(Class.forName(className, false, fallback).getDeclaredConstructor().newInstance())
        }.getOrDefault(emptyList())
    } catch (e: Throwable) {
        emptyList()
    }

    private fun sourcesOf(obj: Any): List<AnimeSource> = when (obj) {
        is AnimeSource -> listOf(obj)
        is AnimeSourceFactory -> obj.createSources()
        else -> emptyList()
    }
}
