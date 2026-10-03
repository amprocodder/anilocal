package com.anilocal.app.data.source

import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.AnimeSummary
import com.anilocal.app.domain.model.DownloadQuality
import com.anilocal.app.domain.model.Episode
import com.anilocal.app.domain.model.VideoServer
import com.anilocal.app.domain.model.VideoStream
import com.anilocal.app.domain.repo.SettingsRepository
import com.anilocal.app.domain.source.AnimeSource
import com.anilocal.app.domain.source.SourceInfo
import com.anilocal.app.domain.source.SourceRegistry
import com.anilocal.app.domain.source.Sources
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue

@OptIn(ExperimentalCoroutinesApi::class)
class SourceStreamRepositoryTest {
    @Test fun mediaProbesOverlapSlowEmptySourcesInsteadOfAddingTheirDelays() = runTest {
        val sources = listOf(
            FakeSource("fast", mapOf("one" to listOf(stream("media")))),
            FakeSource("empty", emptyMap(), searchDelayMs = 2_000),
        )
        val starts = mutableListOf<Long>()
        val repository = SourceStreamRepository(object : SourceRegistry {
            override val sources = MutableStateFlow(sources)
        }, FakeSettings("fast"), StreamSpeedProbe {
            starts += testScheduler.currentTime
            delay(1_000)
            100.0
        }, StandardTestDispatcher(testScheduler))

        assertEquals("media", repository.resolveFastestStream("Anime", 2).url)
        assertEquals(listOf(0L), starts)
        // Sequential resolution then probing takes 3000ms for this same 2000ms + 1000ms work.
        assertEquals(2_000L, testScheduler.currentTime)
        assertEquals("media", repository.resolveFastestStream("Anime", 2, "media").url)
        assertEquals(listOf(0L, 2_000L), starts)
    }

    @Test fun probesStayBoundedWhileEveryDistinctServerIsMeasured() = runTest {
        val source = FakeSource("selected", mapOf("one" to (1..9).map { stream("media-$it") }))
        var active = 0
        var maximumActive = 0
        val measurements = mutableSetOf<String>()
        val repository = SourceStreamRepository(object : SourceRegistry {
            override val sources = MutableStateFlow(listOf(source))
        }, FakeSettings("selected"), StreamSpeedProbe {
            active++
            maximumActive = maxOf(maximumActive, active)
            try {
                delay(1_000)
                measurements += it.url
                100.0
            } finally {
                active--
            }
        }, StandardTestDispatcher(testScheduler))

        repository.resolveFastestStream("Anime", 2)
        assertEquals((1..9).map { "media-$it" }.toSet(), measurements)
        assertTrue("Network probes must not compete without a concurrency bound", maximumActive <= 3)
        assertEquals(0, active)
    }

    @Test fun fastestMeasuredMediaWinsOverQualityAcrossAllSourcesAndServers() = runBlocking {
        val selected = FakeSource("selected", mapOf(
            "one" to listOf(stream("slow", 1080)),
            "two" to listOf(stream("fast", 480)),
        ))
        val other = FakeSource("other", mapOf("one" to listOf(stream("medium", 720))))
        val tested = ConcurrentLinkedQueue<String>()
        val repository = repository(listOf(selected, other), "selected") {
            tested.add(it.url)
            mapOf("slow" to 100.0, "fast" to 300.0, "medium" to 200.0)[it.url]
        }

        assertEquals("fast", repository.resolveFastestStream("Anime", 2).url)
        assertEquals(setOf("slow", "fast", "medium"), tested.toSet())
        assertEquals(setOf("one", "two"), selected.resolvedServers.toSet())
    }

    @Test fun qualityBreaksSpeedTieThenSelectedSourceBreaksRemainingTie() = runBlocking {
        val sources = listOf(
            FakeSource("other", mapOf("one" to listOf(stream("other", 1080)))),
            FakeSource("selected", mapOf("one" to listOf(stream("low", 720), stream("selected", 1080)))),
        )
        assertEquals("selected", repository(sources, "selected") { 100.0 }
            .resolveFastestStream("Anime", 2).url)
    }

    @Test fun failuresInOneSourceOrServerDoNotDiscardHealthyAlternatives() = runBlocking {
        val brokenSource = FakeSource("broken-source", emptyMap(), failSearch = true)
        val missingEpisode = FakeSource("missing", mapOf("one" to listOf(stream("wrong-episode"))), episodeNumber = 1)
        val healthySource = FakeSource("healthy", mapOf(
            "broken-server" to emptyList(),
            "working-server" to listOf(stream("unhealthy"), stream("healthy")),
        ), failedServers = setOf("broken-server"))
        val repository = repository(listOf(brokenSource, missingEpisode, healthySource), "broken-source") {
            if (it.url == "healthy") 100.0 else null
        }

        assertEquals("healthy", repository.resolveFastestStream("Anime", 2).url)
        assertEquals(setOf("broken-server", "working-server"), healthySource.resolvedServers.toSet())
    }

    @Test fun healthyAlternativeIsPreferredButRecoveredFailedUrlRemainsFallback() = runBlocking {
        val source = FakeSource("selected", mapOf("one" to listOf(stream("failed"), stream("alternative"))))
        val repository = repository(listOf(source), "selected") {
            if (it.url == "failed") 1_000.0 else 10.0
        }
        assertEquals("alternative", repository.resolveFastestStream("Anime", 2, "failed").url)

        val transient = repository(listOf(source), "selected") { if (it.url == "failed") 100.0 else null }
        assertEquals("failed", transient.resolveFastestStream("Anime", 2, "failed").url)
    }

    @Test fun everyRecoveryResolvesAndMeasuresAgainWithoutCachedUrlsOrSpeeds() = runBlocking {
        val source = FakeSource("selected", mapOf("one" to listOf(stream("old"))))
        var measurement = 0
        val repository = repository(listOf(source), "selected") { measurement++; 100.0 }
        assertEquals("old", repository.resolveFastestStream("Anime", 2).url)
        source.streams = mapOf("one" to listOf(stream("fresh")))
        assertEquals("fresh", repository.resolveFastestStream("Anime", 2, "old").url)
        assertEquals(2, measurement)
        assertEquals(2, source.searches)
        assertEquals(2, source.resolvedServers.size)
    }

    @Test fun duplicateUrlAndHeadersAreTestedOnceKeepingHighestQuality() = runBlocking {
        val source = FakeSource("selected", mapOf("one" to listOf(
            stream("same", 480).copy(headers = mapOf("Referer" to "a")),
            stream("same", 1080).copy(headers = mapOf("referer" to "a")),
            stream("same", 720).copy(headers = mapOf("Referer" to "b")),
        )))
        val tested = ConcurrentLinkedQueue<VideoStream>()
        val repository = repository(listOf(source), "selected") { tested.add(it); 100.0 }
        assertEquals(1080, repository.resolveFastestStream("Anime", 2).height)
        assertEquals(2, tested.size)
        assertEquals(setOf("a", "b"), tested.map { it.headers.values.single() }.toSet())
    }

    @Test fun cancellationPropagatesIntoAnActiveProbe() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val source = FakeSource("selected", mapOf("one" to listOf(stream("media"))))
        val repository = repository(listOf(source), "selected") {
            started.complete(Unit)
            try { awaitCancellation() } finally { stopped.complete(Unit) }
        }
        val operation = launch { repository.resolveFastestStream("Anime", 2) }
        started.await()
        operation.cancelAndJoin()
        assertTrue(stopped.isCompleted)
    }

    @Test fun downloadPickerStillUsesOnlySelectedSourcesFirstServerWithEveryQuality() = runBlocking {
        val source = FakeSource("selected", mapOf(
            "one" to listOf(stream("same", 480), stream("same", 1080)),
            "two" to listOf(stream("other-server", 720)),
        ))
        val repository = repository(listOf(source), "selected") { error("Download picker must not run probes") }
        assertEquals(listOf(1080, 480), repository.resolveStreams("Anime", 2).map { it.height })
        assertEquals(listOf("one"), source.resolvedServers.toList())
    }

    @Test fun builtInDemoSourcesKeepPlayingCatalogEpisodesBeyondTheirDemoLists() = runBlocking {
        val repository = repository(listOf(SampleSintelSource(), SampleLocalSource()), Sources.SAMPLE_ID) { 100.0 }
        assertEquals("https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4",
            repository.resolveFastestStream("Anime", 100).url)
    }

    @Test fun noHealthyMediaProducesARecoverableFailure() = runBlocking {
        val source = FakeSource("selected", mapOf("one" to listOf(stream("unhealthy"))))
        try {
            repository(listOf(source), "selected") { null }.resolveFastestStream("Anime", 2)
            throw AssertionError("Unreachable media must not be returned")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("No reachable stream"))
        }
    }

    private fun stream(url: String, height: Int = 720) = VideoStream(url, height = height)

    private fun repository(
        sources: List<AnimeSource>,
        selected: String,
        probe: suspend (VideoStream) -> Double?,
    ) = SourceStreamRepository(object : SourceRegistry {
        override val sources = MutableStateFlow(sources)
    }, FakeSettings(selected), StreamSpeedProbe(probe))

    private class FakeSource(
        id: String,
        var streams: Map<String, List<VideoStream>>,
        val failSearch: Boolean = false,
        val failedServers: Set<String> = emptySet(),
        val episodeNumber: Int = 2,
        val searchDelayMs: Long = 0,
    ) : AnimeSource {
        override val info = SourceInfo(id, id)
        var searches = 0
        val resolvedServers = ConcurrentLinkedQueue<String>()
        override suspend fun popular(page: Int) = emptyList<AnimeSummary>()
        override suspend fun search(query: String, page: Int): List<AnimeSummary> {
            searches++
            delay(searchDelayMs)
            if (failSearch) throw IllegalStateException("Source unavailable")
            return listOf(AnimeSummary("anime", query, null))
        }
        override suspend fun detail(animeId: String) = AnimeDetail(animeId, "Anime", null,
            episodes = listOf(Episode("episode", episodeNumber)))
        override suspend fun servers(episode: Episode) = streams.keys.map { VideoServer(it, it, episode.id) }
        override suspend fun resolve(server: VideoServer): List<VideoStream> {
            resolvedServers.add(server.id)
            if (server.id in failedServers) throw IllegalStateException("Server unavailable")
            return streams.getValue(server.id)
        }
    }

    private class FakeSettings(selected: String) : SettingsRepository {
        override val selectedSourceId = flowOf(selected)
        override val autoSkip = flowOf(false)
        override val wifiOnlyDownloads = flowOf(false)
        override val downloadQuality = flowOf(DownloadQuality.P720)
        override val subtitleScale = flowOf(1f)
        override val subtitleBackground = flowOf(false)
        override val malUsername = flowOf("")
        override val malSyncEnabled = flowOf(false)
        override val malLastSynced = flowOf(0L)
        override suspend fun setSelectedSourceId(id: String) = Unit
        override suspend fun setAutoSkip(enabled: Boolean) = Unit
        override suspend fun setWifiOnlyDownloads(enabled: Boolean) = Unit
        override suspend fun setDownloadQuality(quality: DownloadQuality) = Unit
        override suspend fun setSubtitleScale(scale: Float) = Unit
        override suspend fun setSubtitleBackground(enabled: Boolean) = Unit
        override suspend fun setMalUsername(username: String) = Unit
        override suspend fun setMalSyncEnabled(enabled: Boolean) = Unit
        override suspend fun setMalLastSynced(epochMs: Long) = Unit
    }
}
