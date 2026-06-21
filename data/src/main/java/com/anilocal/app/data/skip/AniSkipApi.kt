package com.anilocal.app.data.skip

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * AniSkip v2 — crowd-sourced opening/ending timestamps keyed by MAL id. No API key.
 * GET /v2/skip-times/{malId}/{episode}?types=op&types=ed&episodeLength={sec}
 * (If a deployment expects bracketed params, change the @Query name to "types[]".)
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

data class AniSkipResult(val interval: AniSkipInterval?, val skipType: String?)

data class AniSkipInterval(val startTime: Double?, val endTime: Double?)
