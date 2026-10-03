package com.anilocal.app.data.metadata.anilist

import com.anilocal.app.domain.model.BrowseSort
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AniListCatalogRepositoryTest {
    @Test fun completeLookingHomeWithGraphqlErrorsDisplaysRowsButSeedsNoCaches() = runTest {
        val homeData = AniListData(
            trending = page(1), popularThisSeason = page(2), topAiring = page(3),
            allTimePopular = page(4), upcoming = page(5),
        )
        var attempts = 0
        val api = FakeApi {
            when (++attempts) {
                1 -> GraphResponse(homeData, listOf(GraphError("Cover field temporarily unavailable")))
                2 -> GraphResponse(AniListData(page = page(8)))
                else -> GraphResponse(homeData)
            }
        }
        val repository = AniListCatalogRepository(api, { 0L }, StandardTestDispatcher(testScheduler))
        assertEquals("1", repository.home().trending.single().id)
        // Even a present shelf can contain a failed nested field, so it must reload independently.
        assertEquals("8", repository.trending().single().id)
        val complete = repository.home()
        assertEquals("1", complete.trending.single().id)
        assertEquals(complete, repository.home())
        assertEquals(complete.trending, repository.trending())
        assertEquals(3, attempts)
    }

    @Test fun partialDetailWithGraphqlErrorsDoesNotCacheAMissingEpisodeList() = runTest {
        var attempts = 0
        val api = FakeApi {
            if (++attempts == 1) GraphResponse(
                AniListData(media = media(10)), listOf(GraphError("Episode count temporarily unavailable")),
            ) else GraphResponse(AniListData(media = media(10, episodes = 12)))
        }
        val repository = AniListCatalogRepository(api, { 0L }, StandardTestDispatcher(testScheduler))
        var failed = false
        try {
            repository.detail("10")
        } catch (failure: IllegalStateException) {
            failed = true
            assertEquals("Episode count temporarily unavailable", failure.message)
        }
        assertTrue(failed)
        val detail = repository.detail("10")
        assertEquals(12, detail.episodes.size)
        assertEquals(detail, repository.detail("10"))
        assertEquals(2, attempts)
    }

    @Test fun partialHomeKeepsSuccessfulShelvesAndRetriesMissingShelvesOnNextLoad() = runTest {
        var attempts = 0
        val api = FakeApi {
            if (++attempts == 1) GraphResponse(
                AniListData(trending = page(1), upcoming = PageDto(null)), listOf(GraphError("One shelf unavailable")),
            ) else GraphResponse(AniListData(
                trending = page(1), popularThisSeason = page(2), topAiring = page(3),
                allTimePopular = page(4), upcoming = page(5),
            ))
        }
        val repository = AniListCatalogRepository(api, { 0L }, StandardTestDispatcher(testScheduler))
        val partial = repository.home()
        assertEquals("1", partial.trending.single().id)
        assertTrue(partial.upcoming.isEmpty())
        val complete = repository.home()
        assertEquals("5", complete.upcoming.single().id)
        assertEquals(complete, repository.home())
        assertEquals(2, attempts)
    }

    @Test fun homeUsesOneRequestAndSeedsIndividualRowsAndExploreDefaults() = runTest {
        val api = FakeApi {
            GraphResponse(AniListData(
                trending = page(1), popularThisSeason = page(2), topAiring = page(3),
                allTimePopular = page(4), upcoming = page(5),
            ))
        }
        val repository = AniListCatalogRepository(api, { testScheduler.currentTime }, StandardTestDispatcher(testScheduler))
        val home = repository.home()
        assertEquals(listOf("1", "2", "3", "4", "5"), listOf(
            home.trending, home.popularThisSeason, home.topAiring, home.allTimePopular, home.upcoming,
        ).map { it.single().id })
        assertEquals(home, repository.home())
        assertEquals(home.trending, repository.popular())
        assertEquals(home.popularThisSeason, repository.popularThisSeason())
        assertEquals(home.topAiring, repository.topAiring())
        assertEquals(home.allTimePopular, repository.browse(null, BrowseSort.POPULAR))
        assertEquals(1, api.requests.size)
        val query = api.requests.single().query
        assertTrue(query.contains("trending: Page"))
        assertTrue(query.contains("upcoming: Page"))
    }

    @Test fun detailAndPlayerShareInFlightMetadataThenRefreshAfterTtl() = runTest {
        val release = CompletableDeferred<Unit>()
        val api = FakeApi { release.await(); GraphResponse(AniListData(media = media(10, episodes = 12))) }
        var now = 0L
        val repository = AniListCatalogRepository(api, { now }, StandardTestDispatcher(testScheduler))
        val detail = async { repository.detail("10") }
        val player = async { repository.detail("010") }
        runCurrent()
        assertEquals(1, api.requests.size)
        detail.cancelAndJoin()
        assertFalse(player.isCompleted)
        release.complete(Unit)
        val result = player.await()
        assertEquals(12, result.episodes.size)
        assertEquals("Synopsis", result.synopsis)
        assertEquals(result, repository.detail("10"))
        assertEquals(1, api.requests.size)
        now = 30 * 60 * 1000L
        repository.detail("10")
        assertEquals(2, api.requests.size)
    }

    @Test fun trimmedSearchAndSafelyEncodedGenreHaveIndependentCachedPages() = runTest {
        val api = FakeApi { GraphResponse(AniListData(page = page(1))) }
        val repository = AniListCatalogRepository(api, { 0L }, StandardTestDispatcher(testScheduler))
        repository.search("  Naruto  ")
        repository.search("Naruto")
        val genre = "Action\"\\Special"
        repository.browse(genre, BrowseSort.TRENDING, 1)
        repository.browse(genre, BrowseSort.TRENDING, 1)
        repository.browse(genre, BrowseSort.TRENDING, 2)
        assertEquals(3, api.requests.size)
        assertEquals("Naruto", api.requests[0].variables["search"])
        assertEquals(genre, api.requests[1].variables["genre"])
        assertTrue(api.requests[1].query.contains("genre:\$genre"))
        assertFalse(api.requests[1].query.contains(genre))
        assertEquals(2.0, api.requests[2].variables["page"])
    }

    @Test fun networkAndGraphqlFailuresAreRetriedRatherThanCachedAsEmptyResults() = runTest {
        var attempts = 0
        val api = FakeApi {
            when (++attempts) {
                1 -> throw IOException("No connection")
                2 -> GraphResponse(null, listOf(GraphError("Temporarily unavailable")))
                else -> GraphResponse(AniListData(page = page(8)))
            }
        }
        val repository = AniListCatalogRepository(api, { 0L }, StandardTestDispatcher(testScheduler))
        repeat(2) {
            var failed = false
            try { repository.search("anime") } catch (_: Exception) { failed = true }
            assertTrue(failed)
        }
        assertEquals("8", repository.search("anime").single().id)
        assertEquals(3, attempts)
    }

    @Test fun cancellingLastMappingCallerCancelsApiAndDoesNotCacheNull() = runTest {
        val cancelled = CompletableDeferred<Unit>()
        var attempts = 0
        val api = FakeApi {
            if (++attempts == 1) {
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            } else GraphResponse(AniListData(media = media(8)))
        }
        val repository = AniListCatalogRepository(api, { 0L }, StandardTestDispatcher(testScheduler))
        val request = async { repository.anilistIdForMal(123) }
        runCurrent()
        request.cancelAndJoin()
        runCurrent()
        assertTrue(cancelled.isCompleted)
        assertEquals("8", repository.anilistIdForMal(123))
        assertEquals(2, attempts)
    }

    @Test fun aliasedHomeResponseParsesWithoutLosingShelves() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val response = moshi.adapter(GraphResponse::class.java).fromJson("""
            {"data": {
                "trending": {"media": [{"id": 1}]},
                "popularThisSeason": {"media": []},
                "topAiring": {"media": []},
                "allTimePopular": {"media": []},
                "upcoming": {"media": [{"id": 5}]}
            }}
        """.trimIndent())!!
        assertEquals(1, response.data!!.trending!!.media!!.single().id)
        assertEquals(5, response.data!!.upcoming!!.media!!.single().id)
    }

    private data class Request(val query: String, val variables: Map<String, Any?>)
    private class FakeApi(private val respond: suspend () -> GraphResponse) : AniListApi {
        val requests = mutableListOf<Request>()
        override suspend fun query(body: RequestBody): GraphResponse {
            val buffer = Buffer()
            body.writeTo(buffer)
            val parsed = Moshi.Builder().build().adapter(Map::class.java).fromJson(buffer.readUtf8())!!
            @Suppress("UNCHECKED_CAST")
            requests += Request(parsed["query"] as String, parsed["variables"] as Map<String, Any?>)
            return respond()
        }
    }

    private companion object {
        fun page(id: Int) = PageDto(listOf(media(id)))
        fun media(id: Int, episodes: Int? = null) = MediaDto(
            id, id + 100, TitleDto("Anime $id", "Anime $id", null), CoverDto("poster", null),
            null, "<p>Synopsis</p>", listOf("Action"), episodes,
        )
    }
}
