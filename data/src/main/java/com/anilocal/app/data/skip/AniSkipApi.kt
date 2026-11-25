package com.anilocal.app.data.skip

import com.anilocal.app.data.remote.JsonHttp
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AniSkip v2 — crowd-sourced opening/ending timestamps keyed by MAL id. No API key.
 * GET /v2/skip-times/{malId}/{episode}?types=op&types=ed&episodeLength={sec}
 * Per type: the top-voted submission within ±20s of episodeLength (any length when 0); HTTP 404 when
 * no type matched (thrown as an `HttpException` with code 404). Each result carries the
 * episodeLength it was submitted on (its cut).
 */
@Singleton
class AniSkipApi @Inject constructor(private val http: JsonHttp) {
    suspend fun skipTimes(malId: Int, episode: Int, types: List<String>, episodeLength: Long): AniSkipResponse {
        val url = BASE.newBuilder()
            .addPathSegments("v2/skip-times")
            .addPathSegment(malId.toString())
            .addPathSegment(episode.toString())
            .apply { types.forEach { addQueryParameter("types", it) } }
            .addQueryParameter("episodeLength", episodeLength.toString())
            .build()
        return http.get(url, AniSkipResponse.serializer())
    }

    private companion object {
        val BASE = "https://api.aniskip.com/".toHttpUrl()
    }
}

@Serializable
data class AniSkipResponse(val found: Boolean? = null, val results: List<AniSkipResult>? = null)

@Serializable
data class AniSkipResult(
    val interval: AniSkipInterval? = null,
    val skipType: String? = null,
    val episodeLength: Double? = null,
)

@Serializable
data class AniSkipInterval(val startTime: Double? = null, val endTime: Double? = null)
