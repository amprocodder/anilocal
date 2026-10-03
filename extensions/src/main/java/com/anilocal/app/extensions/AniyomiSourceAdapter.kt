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
import com.anilocal.app.domain.model.Subtitle
import com.anilocal.app.domain.model.VideoServer
import com.anilocal.app.domain.model.VideoStream
import com.anilocal.app.domain.source.AnimeSource
import com.anilocal.app.domain.source.SourceInfo
import com.anilocal.app.domain.source.SourcePreference
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.preferenceKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        runCatching { cat.getPopularAnime(page).animes.map { it.toSummary() } }.getOrDefault(emptyList())
    }

    override suspend fun search(query: String, page: Int): List<AnimeSummary> = withContext(Dispatchers.IO) {
        val cat = src as? AnimeCatalogueSource ?: return@withContext emptyList()
        runCatching { cat.getSearchAnime(page, query, AnimeFilterList()).animes.map { it.toSummary() } }
            .getOrDefault(emptyList())
    }

    override suspend fun detail(animeId: String): AnimeDetail = withContext(Dispatchers.IO) {
        val seed = SAnime.create().apply { url = animeId; title = "" }
        val full = runCatching { src.getAnimeDetails(seed) }.getOrDefault(seed)
        val episodes = runCatching { src.getEpisodeList(seed) }.getOrDefault(emptyList())
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
        // Classic ext-lib-14 path first — the whole yuzono catalog implements only getVideoList(episode),
        // so this resolves in a single request instead of first throwing AbstractMethodError on the
        // lib-16 hoster API. Fall back to the lib-16 getHosterList → getVideoList(hoster) pipeline for
        // sources that implement it. Wrapped so an unimplemented-API AbstractMethodError (lib mismatch)
        // degrades to empty, never a crash.
        val videos = runCatching { src.getVideoList(sEpisode) }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: resolveViaHosters(sEpisode)
        videos.map { it.toVideoStream() }
    }

    /** ext-lib-16 fallback: the first hoster that yields videos wins (sources order them by preference). */
    private suspend fun resolveViaHosters(sEpisode: SEpisode): List<Video> {
        val hosters = runCatching { src.getHosterList(sEpisode) }.getOrDefault(emptyList())
        for (hoster in hosters) {
            val vids = if (hoster.videoList != null && !hoster.lazy) {
                hoster.videoList!!
            } else {
                runCatching { src.getVideoList(hoster) }.getOrDefault(emptyList())
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

    private fun Video.toVideoStream() = VideoStream(
        url = videoUrl,
        mimeType = inferStreamMime(videoUrl),
        headers = headers?.toMap() ?: emptyMap(),
        subtitles = subtitleTracks.map { Subtitle(url = it.url, language = it.lang, label = it.lang) },
        quality = videoTitle.ifBlank { resolution?.let { "${it}p" } },
        height = resolution,
    )

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

        private fun encodeEpisode(url: String, number: Float): String = "$url$EP$number"
    }
}
