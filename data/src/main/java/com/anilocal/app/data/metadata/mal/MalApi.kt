package com.anilocal.app.data.metadata.mal

import com.anilocal.app.data.remote.JsonHttp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a user's **PUBLIC** MyAnimeList list with **no API key and no OAuth**, via the same
 * `load.json` endpoint MAL's own list page calls. Only a username is required.
 *
 * `status=7` returns every category; the result is a flat JSON array paginated by `offset`
 * (MAL serves ~300 entries per page). Per-entry `status` is numeric — see [MalLoadEntry].
 * Requires the user's list privacy to be set to Public on MAL.
 */
@Singleton
class MalApi @Inject constructor(private val http: JsonHttp) {
    suspend fun animeList(username: String, offset: Int = 0, status: Int = 7): List<MalLoadEntry> {
        val url = BASE.newBuilder()
            .addPathSegment("animelist")
            .addPathSegment(username)          // encoded by HttpUrl
            .addPathSegment("load.json")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("status", status.toString())   // 7 = all categories
            .build()
        return http.get(url, ENTRIES, headers = mapOf("User-Agent" to USER_AGENT))
    }

    private companion object {
        val BASE = "https://myanimelist.net/".toHttpUrl()
        val ENTRIES = ListSerializer(MalLoadEntry.serializer())
        // MAL serves load.json to browsers; a bare client UA gets refused.
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}

/**
 * One row from `load.json`. `status` is MAL's numeric list code:
 * 1=watching, 2=completed, 3=on_hold, 4=dropped, 6=plan_to_watch.
 */
@Serializable
data class MalLoadEntry(
    @SerialName("anime_id") val animeId: Int,
    @SerialName("anime_title") val title: String? = null,
    /** MAL's English title, blank when it has none — preferred for display (English is primary). */
    @SerialName("anime_title_eng") val titleEnglish: String? = null,
    @SerialName("anime_image_path") val imagePath: String? = null,
    val status: Int,
    val score: Int? = null,
    @SerialName("num_watched_episodes") val episodesWatched: Int? = null,
    @SerialName("anime_num_episodes") val numEpisodes: Int? = null,
)
