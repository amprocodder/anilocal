package com.anilocal.app.extensions

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.view.ContextThemeWrapper
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.Episode
import com.anilocal.app.domain.model.SkipMarker
import com.anilocal.app.domain.model.Subtitle
import com.anilocal.app.domain.model.VideoServer
import com.anilocal.app.domain.model.VideoStream
import com.anilocal.app.domain.source.AnimeSource
import com.anilocal.app.domain.source.SourceInfo
import com.anilocal.app.domain.source.SourcePreference
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.preferenceKey
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.tachiyomi.animesource.AnimeSource as AniyomiSource

/**
 * Adapts a dynamically-loaded Aniyomi [AniyomiSource] onto AniLocal's [AnimeSource] seam so any
 * installed extension can resolve streams for AniList-browsed titles. AniList stays the browse layer
 * — [popular]/[search] here are only used by `SourceStreamRepository` to find the source's match for
 * an AniList title, then `detail → servers → resolve` returns the playable variants.
 *
 * Mapping notes:
 * - Aniyomi ids are opaque urls/Longs; AniLocal ids are Strings. The source id is `"aniyomi:<id>"`;
 *   an [AnimeSummary]/[AnimeDetail] id is the `SAnime.url`; an [Episode] id packs the `SEpisode.url`
 *   and its `Float` `episode_number` so [servers]/[resolve] can rebuild the exact [SEpisode].
 * - Episodes keep AniLocal's Int ordinal: the source list is sorted ascending by `episode_number`
 *   and assigned 1..N, so fractional/recap episodes fold into the ordinal sequence (no Float model).
 * - Both video paths are handled, classic-first: [resolve] tries the ext-lib-14
 *   `getVideoList(episode)` and only falls back to the ext-lib-16 `getHosterList(episode)` →
 *   `getVideoList(hoster)` pipeline for sources that implement it. The entire yuzono catalog is
 *   ext-lib-14, so trying classic first avoids a wasted hoster network round-trip + an
 *   `AbstractMethodError` (lib-16 abstract `hosterListParse` is unimplemented there) on every resolve.
 *   [servers] returns one logical server per episode (the consumer only resolves the first, and
 *   [resolve] already walks every hoster). Every source call is wrapped so a failing or
 *   lib-mismatched extension degrades to empty, not a crash (matches the app's silent-degrade
 *   convention; `runCatching` catches `Throwable`, including `AbstractMethodError`).
 */
class AniyomiSourceAdapter(
    private val src: AniyomiSource,
    pkg: String? = null,
) : AnimeSource {

    override val info = SourceInfo(
        id = SOURCE_PREFIX + src.id,
        name = src.name,
        lang = src.lang.ifEmpty { "en" },
        isExternal = true,
        configurable = src is ConfigurableAnimeSource,
        pkg = pkg,
    )

    override suspend fun popular(page: Int): List<AnimeSummary> = withContext(Dispatchers.IO) {
        val cat = src as? AnimeCatalogueSource ?: return@withContext emptyList()
        sourceOrEmpty { cat.getPopularAnime(page).animes.map { it.toSummary() } }
    }

    override suspend fun search(query: String, page: Int): List<AnimeSummary> = withContext(Dispatchers.IO) {
        val cat = src as? AnimeCatalogueSource ?: return@withContext emptyList()
        sourceOrEmpty { cat.getSearchAnime(page, query, AnimeFilterList()).animes.map { it.toSummary() } }
    }

    override suspend fun detail(animeId: String): AnimeDetail = withContext(Dispatchers.IO) {
        val seed = SAnime.create().apply { url = animeId; title = "" }
        val full = sourceOr(seed) { src.getAnimeDetails(seed) }
        // A few extensions use fields populated by getAnimeDetails (language/season metadata or a
        // normalized URL) while enumerating episodes. Pass the enriched object through; falling back
        // to the seed above still keeps older sources working when details fail.
        val episodes = sourceOrEmpty { src.getEpisodeList(full) }
        AnimeDetail(
            id = animeId,
            title = full.title.ifBlank { animeId },
            posterUrl = full.thumbnail_url,
            bannerUrl = full.background_url,
            synopsis = full.description.orEmpty(),
            genres = full.getGenres() ?: emptyList(),
            idMal = null,
            episodes = episodes.toDomainEpisodes(),
        )
    }

    override suspend fun servers(episode: Episode): List<VideoServer> {
        // One logical server per episode. We deliberately don't enumerate lib-16 hosters as separate
        // servers: SourceStreamRepository only resolves servers().firstOrNull(), and resolve() below
        // already walks every hoster. This also avoids a wasted hoster network round-trip for the
        // lib-14 sources that make up the whole yuzono catalog (they have no hoster API). A future
        // server-picker UI could reintroduce per-hoster servers.
        return listOf(VideoServer(id = episode.id, name = src.name, episodeId = episode.id))
    }

    override suspend fun resolve(server: VideoServer): List<VideoStream> = withContext(Dispatchers.IO) {
        val sEpisode = server.episodeId.toSEpisode()
        // Pick the pipeline the extension actually implements. The classic ext-lib-14 path
        // (getVideoList(episode)) is the default — most of the yuzono catalog implements only that,
        // and it resolves in a single request. A source that implements the lib-16 hoster API instead
        // goes hosters-first: for it the classic call only reaches AnimeHttpSource's default, which
        // GETs baseUrl + episode.url and hands the page to a videoListParse it doesn't implement — a
        // wasted round trip (or, with a token-style url, an instant "Invalid URL host"). Each path
        // falls back to the other, and every call is wrapped so an unimplemented-API
        // AbstractMethodError (lib mismatch) degrades to empty, never a crash.
        val videos = if (implementsHosterApi) {
            resolveViaHosters(sEpisode).ifEmpty { sourceOrNull { src.getVideoList(sEpisode) }.orEmpty() }
        } else {
            sourceOrNull { src.getVideoList(sEpisode) }?.takeIf { it.isNotEmpty() }
                ?: resolveViaHosters(sEpisode)
        }
        // Extensions occasionally return a placeholder Video with an empty URL when a hoster
        // failed. Drop it here so the source pipeline can try another source instead of handing an
        // unplayable item to Media3 (which would force a visible player restart).
        videos.mapNotNull { it.toVideoStreamOrNull() }
    }

    /**
     * Whether the extension's own classes (not the vendored base classes it inherits) implement the
     * ext-lib-16 hoster API. Shared base classes an extension bundles (multisrc, lib) count as its own.
     */
    private val implementsHosterApi: Boolean by lazy {
        runCatching {
            var c: Class<*>? = src.javaClass
            while (c != null && !c.name.startsWith(HOST_SOURCE_API)) {
                if (c.declaredMethods.any { it.name in HOSTER_API_METHODS }) return@runCatching true
                c = c.superclass
            }
            false
        }.getOrDefault(false)
    }

    /** ext-lib-16 pipeline: the first hoster that yields videos wins (sources order them by preference). */
    private suspend fun resolveViaHosters(sEpisode: SEpisode): List<Video> {
        val hosters = sourceOrEmpty { src.getHosterList(sEpisode) }
        for (hoster in hosters) {
            // A single broken hoster must not suppress later mirrors. Some extensions lazily
            // populate videoList and others throw while reading a stale hoster; isolate each one.
            val vids = sourceOrEmpty {
                if (hoster.videoList != null && !hoster.lazy) hoster.videoList!!
                else src.getVideoList(hoster)
            }
            if (vids.isNotEmpty()) return vids
        }
        return emptyList()
    }

    override suspend fun preferences(): List<SourcePreference> = withContext(Dispatchers.IO) {
        val configurable = src as? ConfigurableAnimeSource ?: return@withContext emptyList()
        // Build a real PreferenceScreen, let the source populate it, then read each preference's
        // metadata + currently-persisted value. The screen is backed by the SAME SharedPreferences
        // (source_$id) the source reads, so values reflect what's stored. Whole thing is wrapped:
        // a source that misbehaves in setupPreferenceScreen degrades to "no settings", never crashes.
        val screen = runCatching { buildPreferenceScreen(configurable) }.getOrNull()
            ?: return@withContext emptyList()
        (0 until screen.preferenceCount).mapNotNull { screen.getPreference(it).toSourcePreference() }
    }

    override suspend fun setPreference(key: String, value: Any?) {
        withContext(Dispatchers.IO) {
            val configurable = src as? ConfigurableAnimeSource ?: return@withContext
            // Write straight to the source's own SharedPreferences (source_$id) with the type the
            // androidx Preference persists, which is exactly what the source reads back on its next
            // request (most extensions read preferences lazily, so the change applies immediately).
            runCatching {
                val editor = configurable.getSourcePreferences().edit()
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    is String -> editor.putString(key, value)
                    null -> editor.remove(key)
                    else -> editor.putString(key, value.toString())
                }
                editor.apply()
            }
        }
    }

    @SuppressLint("RestrictedApi") // PreferenceManager(ctx)+createPreferenceScreen are the only way to
    // build a PreferenceScreen outside a PreferenceFragmentCompat; public, just @RestrictTo.
    private fun buildPreferenceScreen(configurable: ConfigurableAnimeSource): PreferenceScreen {
        // Wrap the app context in androidx.preference's theme overlay so the widgets a source creates
        // (EditTextPreference/ListPreference/…) can resolve their style attrs off any base app theme.
        val context: Context = ContextThemeWrapper(
            Injekt.get<Application>(),
            androidx.preference.R.style.PreferenceThemeOverlay,
        )
        val manager = PreferenceManager(context).apply {
            sharedPreferencesName = configurable.preferenceKey() // "source_$id" — same store the source uses
            sharedPreferencesMode = Context.MODE_PRIVATE
        }
        val screen = manager.createPreferenceScreen(context)
        configurable.setupPreferenceScreen(screen)
        return screen
    }

    /** Maps the four androidx preference kinds Aniyomi extensions use; unknown kinds are dropped. */
    private fun Preference.toSourcePreference(): SourcePreference? {
        val prefKey = key ?: return null
        val prefTitle = title?.toString() ?: prefKey
        val prefSummary = summary?.toString()
        return when (this) {
            is TwoStatePreference ->
                SourcePreference.Toggle(prefKey, prefTitle, prefSummary, isChecked)
            // MultiSelectListPreference extends DialogPreference (NOT ListPreference) — check it first.
            is MultiSelectListPreference ->
                SourcePreference.MultiSelect(
                    prefKey, prefTitle, prefSummary,
                    values ?: emptySet(),
                    entries.orEmptyStrings(),
                    entryValues.orEmptyStrings(),
                )
            is ListPreference ->
                SourcePreference.Select(
                    prefKey, prefTitle, prefSummary,
                    value ?: "",
                    entries.orEmptyStrings(),
                    entryValues.orEmptyStrings(),
                )
            is EditTextPreference ->
                SourcePreference.EditText(prefKey, prefTitle, prefSummary, text ?: "")
            else -> null
        }
    }

    private fun Array<CharSequence>?.orEmptyStrings(): List<String> =
        this?.map(CharSequence::toString) ?: emptyList()

    /** Extension failures are isolated so one broken plugin cannot abort a source race, but caller
     * cancellation must still propagate to stop in-flight network work when the player closes. */
    private suspend inline fun <T> sourceOr(default: T, crossinline block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        default
    }

    private suspend inline fun <T> sourceOrEmpty(crossinline block: suspend () -> List<T>): List<T> =
        sourceOr(emptyList(), block)

    private suspend inline fun <T> sourceOrNull(crossinline block: suspend () -> T): T? =
        sourceOr(null, block)

    // ---- model mapping ----

    private fun SAnime.toSummary() = AnimeSummary(id = url, title = title, posterUrl = thumbnail_url)

    private fun List<SEpisode>.toDomainEpisodes(): List<Episode> =
        sortedBy { it.episode_number }.mapIndexed { i, se ->
            Episode(
                id = encodeEpisode(se.url, se.episode_number),
                number = i + 1,
                title = se.name.ifBlank { null },
                thumbnailUrl = se.preview_url,
            )
        }

    private fun Video.toVideoStreamOrNull(): VideoStream? {
        if (videoUrl.isBlank()) return null
        return VideoStream(
            url = videoUrl,
            mimeType = inferStreamMime(videoUrl),
            headers = withJarCookies(videoUrl, headers?.toMap() ?: emptyMap()),
            subtitles = subtitleTracks.filter { it.url.isNotBlank() }
                .map { Subtitle(url = it.url, language = it.lang, label = it.lang) },
            quality = videoTitle.ifBlank { resolution?.let { "${it}p" } },
            height = resolution,
            skips = skipMarkers(),
        )
    }

    /**
     * The source's own intro/outro windows for this file. Sites behind the HiAnime-family players
     * (megacloud/megaplay — Anikoto & co.) publish them per stream, so unlike AniSkip they already
     * account for this cut's pre-roll. Unknown windows arrive as 0..0 and are dropped; at most one
     * window per type (the earliest). Recap/Other chapters have no skip control, so they're ignored.
     */
    private fun Video.skipMarkers(): List<SkipMarker> =
        runCatching { timestamps }.getOrNull().orEmpty()
            .mapNotNull { ts ->
                val type = when (ts.type) {
                    ChapterType.Opening, ChapterType.MixedOp -> SkipMarker.Type.INTRO
                    ChapterType.Ending -> SkipMarker.Type.OUTRO
                    else -> return@mapNotNull null
                }
                val startMs = (ts.start * 1000).toLong()
                val endMs = (ts.end * 1000).toLong()
                if (startMs < 0 || endMs <= startMs) null else SkipMarker(type, startMs, endMs)
            }
            .sortedBy { it.startMs }
            .distinctBy { it.type }

    /**
     * Carry the cookie jar's cookies for the stream host into the stream's own headers. The resolve
     * legs run on NetworkHelper's OkHttpClient (AndroidCookieJar + CloudflareInterceptor), so a
     * solved challenge leaves cf_clearance in webkit's CookieManager; playback runs on Media3's
     * HttpURLConnection, which never consults it (Android installs no default CookieHandler), so a
     * CF-gated media host resolved fine and then 403'd. Host-scoped, and a source's own Cookie
     * header always wins.
     */
    private fun withJarCookies(url: String, headers: Map<String, String>): Map<String, String> {
        if (headers.keys.any { it.equals("Cookie", ignoreCase = true) }) return headers
        val httpUrl = url.toHttpUrlOrNull() ?: return headers
        val cookies = runCatching { Injekt.get<NetworkHelper>().cookieJar.get(httpUrl) }
            .getOrNull().orEmpty()
        if (cookies.isEmpty()) return headers
        return headers + ("Cookie" to cookies.joinToString("; ") { "${it.name}=${it.value}" })
    }

    // Aniyomi's Video carries no format field, so infer the container from the URL. "m3u8" is a
    // distinctive token, so match it anywhere and WITHOUT the dot — some extensions serve via a local
    // proxy whose path is the bare word, e.g. "http://localhost:44311/m3u8?url=<encoded>" (Animetsu)
    // or ".../master.m3u8?token=…". DASH keeps the leading "." since bare "mpd" would false-match
    // ordinary words (e.g. "tempdir"). null lets Media3 sniff a progressive container (mp4/mkv/…).
    private fun inferStreamMime(url: String): String? = when {
        url.contains("m3u8", ignoreCase = true) -> HLS_MIME
        url.contains(".mpd", ignoreCase = true) -> DASH_MIME
        else -> null
    }

    private fun String.toSEpisode(): SEpisode {
        val sep = lastIndexOf(EP)
        val epUrl = if (sep < 0) this else substring(0, sep)
        val number = if (sep < 0) 1f else substring(sep + 1).toFloatOrNull() ?: 1f
        return SEpisode.create().apply {
            url = epUrl
            episode_number = number
        }
    }

    companion object {
        const val SOURCE_PREFIX = "aniyomi:"
        private const val HLS_MIME = "application/x-mpegURL"
        private const val DASH_MIME = "application/dash+xml"

        // Control-char delimiter that never appears in URLs/episode names.
        private const val EP = '\u0001' // SEpisode url / episode_number, packed into Episode.id

        /** The vendored source API's package: the host's base classes, where the class walk stops. */
        private const val HOST_SOURCE_API = "eu.kanade.tachiyomi.animesource."
        private val HOSTER_API_METHODS = setOf(
            "getHosterList", "hosterListRequest", "hosterListParse", "hosterListSelector", "hosterFromElement",
        )

        private fun encodeEpisode(url: String, number: Float): String = "$url$EP$number"
    }
}
