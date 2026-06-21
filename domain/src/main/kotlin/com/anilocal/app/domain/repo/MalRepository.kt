package com.anilocal.app.domain.repo

import com.anilocal.app.domain.model.MalListEntry
import com.anilocal.app.domain.model.MalStatus
import kotlinx.coroutines.flow.Flow

/**
 * Read-only MyAnimeList sync: mirrors a user's PUBLIC list (by username, via the official
 * Client-ID API) into a local Room table. No OAuth, no writing back to MAL.
 */
interface MalRepository {
    /** The locally-mirrored list for a status, observable for the Library tab. */
    fun list(status: MalStatus): Flow<List<MalListEntry>>

    /** Pull the user's public list from MAL and replace the local mirror. Returns count. */
    suspend fun sync(): Result<Int>

    /** Sync only if configured + enabled + a username is set + the throttle window has passed. */
    suspend fun syncIfDue()
}
