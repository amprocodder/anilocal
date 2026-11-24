package com.anilocal.app.data.skip

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * AniSkip v2 — crowd-sourced opening/ending timestamps keyed by MAL id. No API key.
 * GET /v2/skip-times/{malId}/{episode}?types=op&types=ed&episodeLength={sec}
 * Per type: the top-voted submission within ±20s of episodeLength (any length when 0); HTTP 404 when
 * no type matched. Each result carries the episodeLength it was submitted on (its cut).
 */
interface AniSkipApi {
    @GET("v2/skip-times/{malId}/{episode}")
    suspend fun skipTimes(
        @Path("malId") malId: Int,
        @Path("episode") episode: Int,
        @Query("types") types: List<String>,
        @Query("episodeLength") episodeLength: Long,
    ): AniSkipResponse
}

data class AniSkipResponse(val found: Boolean?, val results: List<AniSkipResult>?)

data class AniSkipResult(val interval: AniSkipInterval?, val skipType: String?, val episodeLength: Double?)

data class AniSkipInterval(val startTime: Double?, val endTime: Double?)
