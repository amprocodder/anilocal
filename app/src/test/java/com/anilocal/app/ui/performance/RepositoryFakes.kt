package com.anilocal.app.ui.performance

import com.anilocal.app.domain.model.*
import com.anilocal.app.domain.repo.*
import com.anilocal.app.domain.source.Sources
import kotlinx.coroutines.flow.MutableStateFlow

internal open class FakeCatalog : CatalogRepository {
    var searchBlock: suspend (String) -> List<AnimeSummary> = { listOf(summary(it)) }
    var browseBlock: suspend (String?, BrowseSort) -> List<AnimeSummary> = { _, _ -> listOf(summary("browse")) }
    var detailBlock: suspend (String) -> AnimeDetail = { AnimeDetail(it, "Title", null) }
    val searches = mutableListOf<String>()
    val browses = mutableListOf<Pair<String?, BrowseSort>>()
    override suspend fun search(query: String): List<AnimeSummary> {
        searches += query
        return searchBlock(query)
    }
    override suspend fun browse(genre: String?, sort: BrowseSort, page: Int): List<AnimeSummary> {
        browses += genre to sort
        return browseBlock(genre, sort)
    }
    override suspend fun popular(page: Int) = emptyList<AnimeSummary>()
    override suspend fun detail(animeId: String) = detailBlock(animeId)
    override suspend fun trending(page: Int) = emptyList<AnimeSummary>()
    override suspend fun popularThisSeason(page: Int) = emptyList<AnimeSummary>()
    override suspend fun topAiring(page: Int) = emptyList<AnimeSummary>()
    override suspend fun allTimePopular(page: Int) = emptyList<AnimeSummary>()
    override suspend fun upcoming(page: Int) = emptyList<AnimeSummary>()
    override suspend fun anilistIdForMal(malId: Int): String? = malId.toString()
}

internal class FakeDownloads : DownloadRepository {
    override val downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val enqueued = mutableListOf<VideoStream>()
    val enqueuedEpisodes = mutableListOf<Int>()
    var enqueueBlock: suspend () -> Unit = {}
    override fun downloadedAnimeIds() = MutableStateFlow(emptySet<String>())
    override fun downloadedEpisodes(animeId: String) = MutableStateFlow(emptySet<Int>())
    override suspend fun enqueue(detail: AnimeDetail, episode: Episode, stream: VideoStream, markers: List<SkipMarker>) {
        enqueued += stream
        enqueuedEpisodes += episode.number
        enqueueBlock()
    }
    override suspend fun getOffline(animeId: String, episodeNumber: Int): OfflineEpisode? = null
    override fun pause(id: String) = Unit
    override fun resume(id: String) = Unit
    override suspend fun remove(id: String) = Unit
}

internal class FakeStreams : StreamRepository {
    var resolutions = 0
    var resolveBlock: suspend () -> List<VideoStream> = { listOf(VideoStream("video")) }
    override suspend fun resolveStreams(animeTitle: String, episodeNumber: Int): List<VideoStream> {
        resolutions++
        return resolveBlock()
    }
    override suspend fun resolveStream(animeTitle: String, episodeNumber: Int) = resolveStreams(animeTitle, episodeNumber).first()
    override suspend fun resolveFastestStream(animeTitle: String, episodeNumber: Int, failedStreamUrl: String?) = resolveStream(animeTitle, episodeNumber)
}

internal class FakeSkip : SkipRepository {
    var requests = 0
    var markersBlock: suspend () -> List<SkipMarker> = { emptyList() }
    override suspend fun markers(idMal: Int?, episodeNumber: Int, episodeLengthSec: Long): List<SkipMarker> {
        requests++
        return markersBlock()
    }
}

internal class FakeSettings : SettingsRepository {
    override val autoSkip = MutableStateFlow(true)
    override val wifiOnlyDownloads = MutableStateFlow(true)
    override val downloadQuality = MutableStateFlow(DownloadQuality.AUTO)
    override val subtitleScale = MutableStateFlow(1f)
    override val subtitleBackground = MutableStateFlow(true)
    override val selectedSourceId = MutableStateFlow(Sources.SAMPLE_ID)
    override val malUsername = MutableStateFlow("")
    override val malSyncEnabled = MutableStateFlow(false)
    override val malLastSynced = MutableStateFlow(0L)
    var usernameWriteBlock: suspend (String) -> Unit = {}
    val usernameWrites = mutableListOf<String>()
    override suspend fun setAutoSkip(enabled: Boolean) { autoSkip.value = enabled }
    override suspend fun setWifiOnlyDownloads(enabled: Boolean) { wifiOnlyDownloads.value = enabled }
    override suspend fun setDownloadQuality(quality: DownloadQuality) { downloadQuality.value = quality }
    override suspend fun setSubtitleScale(scale: Float) { subtitleScale.value = scale }
    override suspend fun setSubtitleBackground(enabled: Boolean) { subtitleBackground.value = enabled }
    override suspend fun setSelectedSourceId(id: String) { selectedSourceId.value = id }
    override suspend fun setMalUsername(username: String) {
        usernameWrites += username
        usernameWriteBlock(username)
        malUsername.value = username
    }
    override suspend fun setMalSyncEnabled(enabled: Boolean) { malSyncEnabled.value = enabled }
    override suspend fun setMalLastSynced(epochMs: Long) { malLastSynced.value = epochMs }
}

internal class FakeMal : MalRepository {
    val entries = MutableStateFlow<List<MalListEntry>>(emptyList())
    var syncCalls = 0
    var syncBlock: suspend () -> Result<Int> = { Result.success(3) }
    override fun all() = entries
    override fun list(status: MalStatus) = entries
    override suspend fun sync(): Result<Int> {
        syncCalls++
        return syncBlock()
    }
    override suspend fun syncIfDue() = Unit
}

internal fun summary(id: String, malId: Int? = null) = AnimeSummary(id, "Title $id", null, malId)
internal fun malEntry(id: Int, status: MalStatus = MalStatus.WATCHING) =
    MalListEntry(id, "MAL $id", null, status, 0, 0, null)
