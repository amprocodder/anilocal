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
import com.anilocal.app.domain.source.AnimeSource as DomainAnimeSource

/**
 * Discovers installed Aniyomi anime-extension APKs and adapts their sources onto the domain seam.
 * Ported from aniyomi's AnimeExtensionLoader (the discovery/metadata/classloader mechanism), trimmed
 * to what AniLocal needs: no private-extension dir, no NSFW gate, no install receiver. Extensions are
 * loaded from the user's sideloaded (shared) packages.
 *
 * NOTE: signature/trust gating is intentionally deferred — every installed extension the user
 * sideloaded is loaded. A `TrustExtension` gate (SHA-256 of the signing cert, prompt on first/changed
 * signature) is the natural follow-up before exposing in-app installs from arbitrary repos (Phase 4).
 */
object AnimeExtensionLoader {

    private const val EXTENSION_FEATURE = "tachiyomi.animeextension"
    private const val METADATA_SOURCE_CLASS = "tachiyomi.animeextension.class"
    private const val LIB_VERSION_MIN = 12.0
    private const val LIB_VERSION_MAX = 16.0

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA

    /**
     * All installed extensions' sources, each wrapped in an [AniyomiSourceAdapter]. Seeds the Injekt
     * runtime first so a loaded `AnimeHttpSource` can construct. Safe to call off the main thread.
     */
    fun loadSources(context: Context): List<DomainAnimeSource> {
        AniyomiRuntime.seed(context.applicationContext as Application)
        val pm = context.packageManager
        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PACKAGE_FLAGS)
        }
        return installed.asSequence()
            .filter { it.isAnimeExtension() }
            .flatMap { runCatching { loadPackage(context, it) }.getOrDefault(emptyList()) }
            .map { AniyomiSourceAdapter(it) }
            .toList()
    }

    private fun PackageInfo.isAnimeExtension(): Boolean =
        reqFeatures?.any { it.name == EXTENSION_FEATURE } == true

    private fun loadPackage(context: Context, pkgInfo: PackageInfo): List<AnimeSource> {
        val appInfo = pkgInfo.applicationInfo ?: return emptyList()

        // Lib version is the major.minor of the extension's versionName (e.g. "16.0.3" -> 16.0).
        val libVersion = pkgInfo.versionName?.substringBeforeLast('.')?.toDoubleOrNull() ?: return emptyList()
        if (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) return emptyList()

        val classNames = appInfo.metaData?.getString(METADATA_SOURCE_CLASS) ?: return emptyList()
        val classLoader = ChildFirstPathClassLoader(appInfo.sourceDir, null, context.classLoader)

        return classNames.split(";").flatMap { raw ->
            val className = raw.trim().let { if (it.startsWith(".")) pkgInfo.packageName + it else it }
            instantiate(className, classLoader, appInfo.sourceDir, context)
        }
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
