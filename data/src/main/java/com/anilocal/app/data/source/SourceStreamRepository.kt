package com.anilocal.app.data.source

import com.anilocal.app.domain.model.VideoStream
import com.anilocal.app.domain.repo.SettingsRepository
import com.anilocal.app.domain.repo.StreamRepository
import com.anilocal.app.domain.source.AnimeSource
import com.anilocal.app.domain.source.SourceRegistry
import com.anilocal.app.domain.source.Sources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges catalog metadata (AniList) to playable streams via the user's selected [AnimeSource]:
 * find the title in the source, match the episode number, resolve a server to its quality
 * variants. The active source is taken from [SourceRegistry] by the persisted selected-source id,
 * falling back to the lawful built-in sample (then any available source) so playback never depends
 * on a single compile-time binding. Playback's speed-tested resolver freshly checks every source
 * and server, preferring the selected source when speed and quality tie. Browsing stays on AniList.
 */
@Singleton
class SourceStreamRepository internal constructor(
    private val registry: SourceRegistry,
    private val settings: SettingsRepository,
    private val speedProbe: StreamSpeedProbe,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StreamRepository {

    @Inject constructor(
        registry: SourceRegistry,
        settings: SettingsRepository,
        client: OkHttpClient,
    ) : this(registry, settings, HttpStreamSpeedProbe(client))

    private suspend fun activeSource(): AnimeSource {
        val selected = settings.selectedSourceId.first()
        return registry.get(selected)
            ?: registry.get(Sources.SAMPLE_ID)
            ?: registry.sources.value.firstOrNull()
            ?: error("no stream sources available")
    }

    override suspend fun resolveStreams(animeTitle: String, episodeNumber: Int): List<VideoStream> =
        withContext(dispatcher) {
            val source = activeSource()
            val match = source.search(animeTitle, page = 1).firstOrNull()
                ?: error("source '${source.info.name}': no match for \"$animeTitle\"")
            val detail = source.detail(match.id)
            val episode = detail.episodes.firstOrNull { it.number == episodeNumber }
                ?: detail.episodes.firstOrNull()
                ?: error("source '${source.info.name}': no episodes for \"$animeTitle\"")
            val server = source.servers(episode).firstOrNull()
                ?: error("source '${source.info.name}': no servers for episode of \"$animeTitle\"")
            source.resolve(server).sortedByDescending { it.height ?: 0 }
        }

    override suspend fun resolveStream(animeTitle: String, episodeNumber: Int): VideoStream =
        resolveStreams(animeTitle, episodeNumber).firstOrNull()
            ?: error("no stream for \"$animeTitle\"")

    override suspend fun resolveFastestStream(
        animeTitle: String,
        episodeNumber: Int,
        failedStreamUrl: String?,
    ): VideoStream = withContext(dispatcher) {
        val selected = settings.selectedSourceId.first()
        val sources = registry.sources.value.sortedBy { if (it.info.id == selected) 0 else 1 }
        val candidates = ConcurrentHashMap<StreamKey, Candidate>()
        val startedProbes = ConcurrentHashMap.newKeySet<StreamKey>()
        val measured = ConcurrentHashMap<StreamKey, Double>()
        val sourceSlots = Semaphore(3)
        val serverSlots = Semaphore(3)
        val probeSlots = Semaphore(3)

        // Start testing each resolved URL immediately. A slow source must not prevent healthy
        // sources' probes from using the same time, and every restart still takes fresh samples.
        withTimeoutOrNull(SESSION_BUDGET_MS) {
            coroutineScope {
                val session = this
                fun enqueueProbe(candidate: Candidate) {
                    val stream = candidate.stream
                    val key = StreamKey(
                        stream.url,
                        stream.headers.entries.associate {
                            it.key.lowercase(Locale.ROOT) to it.value
                        }.toSortedMap(),
                    )
                    candidates.compute(key) { _, previous ->
                        if (previous == null || candidate.betterThan(previous)) candidate else previous
                    }
                    if (!startedProbes.add(key)) return
                    // The probe is a sibling of resolution, so a source timeout cannot cancel it.
                    session.launch {
                        probeSlots.withPermit {
                            ignoringSourceFailure {
                                withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                                    speedProbe.bytesPerSecond(stream)
                                        ?.takeIf { it > 0 && it.isFinite() }
                                        ?.let { measured[key] = it }
                                }
                            }
                        }
                    }
                }
                launch {
                    withTimeoutOrNull(RESOLUTION_BUDGET_MS) {
                        coroutineScope {
                            sources.forEachIndexed { sourceIndex, source ->
                                launch {
                                    sourceSlots.withPermit {
                                        resolveCandidates(source, animeTitle, episodeNumber, serverSlots) { serverIndex, variantIndex, stream ->
                                            enqueueProbe(Candidate(stream, sourceIndex, serverIndex, variantIndex))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Duplicate quality labels share one probe, retaining the best quality and stable tie order.
        val healthy = measured.mapNotNull { (key, speed) ->
            candidates[key]?.let { MeasuredStream(it, speed) }
        }
        val alternatives = healthy.filter { it.candidate.stream.url != failedStreamUrl }
        (alternatives.ifEmpty { healthy }).sortedWith(
            compareByDescending<MeasuredStream> { it.bytesPerSecond }
                .thenByDescending { it.candidate.stream.height ?: 0 }
                .thenBy { it.candidate.sourceIndex }
                .thenBy { it.candidate.serverIndex }
                .thenBy { it.candidate.variantIndex },
        ).firstOrNull()?.candidate?.stream
            ?: error("No reachable stream for \"$animeTitle\", episode $episodeNumber")
    }

    private suspend fun resolveCandidates(
        source: AnimeSource,
        animeTitle: String,
        episodeNumber: Int,
        serverSlots: Semaphore,
        onStream: (Int, Int, VideoStream) -> Unit,
    ) = ignoringSourceFailure {
        withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
            val match = source.search(animeTitle, page = 1).firstOrNull() ?: return@withTimeoutOrNull
            val episodes = source.detail(match.id).episodes
            val episode = episodes.firstOrNull { it.number == episodeNumber }
                // Built-in demos reuse one clip for every catalog episode.
                ?: episodes.firstOrNull().takeIf {
                    source.info.id == Sources.SAMPLE_ID || source.info.id == "sample-sintel"
                }
                ?: return@withTimeoutOrNull
            val servers = source.servers(episode)
            coroutineScope {
                servers.forEachIndexed { serverIndex, server ->
                    launch {
                        serverSlots.withPermit {
                            ignoringSourceFailure {
                                withTimeoutOrNull(SERVER_TIMEOUT_MS) {
                                    source.resolve(server).forEachIndexed { variantIndex, stream ->
                                        onStream(serverIndex, variantIndex, stream)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun ignoringSourceFailure(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A failed source, server or probe must not discard another server's healthy stream.
        }
    }

    private data class Candidate(
        val stream: VideoStream,
        val sourceIndex: Int,
        val serverIndex: Int,
        val variantIndex: Int,
    ) {
        fun betterThan(other: Candidate): Boolean =
            (stream.height ?: 0) > (other.stream.height ?: 0) ||
                ((stream.height ?: 0) == (other.stream.height ?: 0) && candidateOrder.compare(this, other) < 0)
    }

    private data class StreamKey(val url: String, val headers: Map<String, String>)

    private data class MeasuredStream(val candidate: Candidate, val bytesPerSecond: Double)

    private companion object {
        const val RESOLUTION_BUDGET_MS = 12_000L
        const val SOURCE_TIMEOUT_MS = 8_000L
        const val SERVER_TIMEOUT_MS = 4_000L
        const val SESSION_BUDGET_MS = 16_000L
        const val PROBE_TIMEOUT_MS = 5_000L
        val candidateOrder = compareBy<Candidate>({ it.sourceIndex }, { it.serverIndex }, { it.variantIndex })
    }
}
