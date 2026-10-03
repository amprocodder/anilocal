package com.anilocal.app.data.source

import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.VideoStream
import com.anilocal.app.domain.source.AnimeSource
import com.anilocal.app.domain.source.SourceRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

internal data class RecoveredStream(val source: AnimeSource, val detail: AnimeDetail, val stream: VideoStream)

/**
 * A recovery deliberately has no cache/pin dependency. Resolve fresh episode URLs, then measure
 * actual media bytes. Blocking extension losers drain detached, like AutoSourceSelector's races;
 * accepted candidates are frozen before cancellation so late returns never alter the result.
 */
internal class FreshStreamRecovery(
    private val registry: SourceRegistry,
    private val speedProbe: StreamSpeedProbe,
    private val appScope: CoroutineScope,
    private val resolutionPermit: suspend (suspend () -> Unit) -> Unit,
) {
    private val mediaGate = Semaphore(MAX_MEDIA_PROBES)

    suspend fun resolve(
        title: String,
        episodeNumber: Int,
        failedStreamUrl: String?,
        selectedSourceId: String,
    ): RecoveredStream {
        val available = registry.sources.value.takeIf { it.isNotEmpty() }
            ?: withTimeoutOrNull(REGISTRY_WAIT_MS) { registry.sources.first { it.isNotEmpty() } }
            ?: error("no stream sources available")
        val sources = available.sortedBy { if (it.info.id == selectedSourceId) 0 else 1 }
        val candidates = ArrayList<Candidate>()
        var accepting = true
        val completed = CompletableDeferred<Unit>()
        val remaining = AtomicInteger(sources.size)
        val resolutionScope = CoroutineScope(appScope.coroutineContext +
            SupervisorJob(appScope.coroutineContext[Job]) + Dispatchers.IO)
        val fresh: List<Candidate>
        try {
            sources.forEachIndexed { sourceIndex, source ->
                resolutionScope.launch {
                    try {
                        resolutionPermit {
                            ignoringFailure {
                                withTimeoutOrNull(SOURCE_TIMEOUT_MS) sourceTimeout@ {
                                    coroutineContext.ensureActive()
                                    val match = source.search(title, 1).firstOrNull() ?: return@sourceTimeout
                                    coroutineContext.ensureActive()
                                    val detail = source.detail(match.id)
                                    coroutineContext.ensureActive()
                                    // Recovery must never restart a different episode as a fallback.
                                    val episode = detail.episodes.firstOrNull { it.number == episodeNumber }
                                        ?: return@sourceTimeout
                                    val servers = source.servers(episode)
                                    coroutineContext.ensureActive()
                                    servers.forEachIndexed { serverIndex, server ->
                                        coroutineContext.ensureActive()
                                        ignoringFailure {
                                            withTimeoutOrNull(SERVER_TIMEOUT_MS) {
                                                val variants = mergeSubtitles(source.resolve(server))
                                                coroutineContext.ensureActive()
                                                synchronized(candidates) {
                                                    if (accepting) variants.forEachIndexed { variantIndex, stream ->
                                                        candidates.add(Candidate(
                                                            RecoveredStream(source, detail, stream),
                                                            sourceIndex, serverIndex, variantIndex,
                                                        ))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                        if (remaining.decrementAndGet() == 0) completed.complete(Unit)
                    }
                }
            }
            withTimeoutOrNull(RESOLUTION_BUDGET_MS) { completed.await() }
            coroutineContext.ensureActive()
            fresh = synchronized(candidates) {
                accepting = false
                candidates.sortedWith(candidateOrder)
            }
        } finally {
            synchronized(candidates) { accepting = false }
            resolutionScope.cancel()
        }

        val unique = fresh.groupBy { candidate ->
            candidate.result.stream.url to candidate.result.stream.headers.entries
                .associate { it.key.lowercase(Locale.ROOT) to it.value }.toSortedMap()
        }.values.map { duplicates ->
            val best = duplicates.maxBy { it.result.stream.height ?: 0 }
            val subtitles = (best.result.stream.subtitles + duplicates.flatMap { it.result.stream.subtitles })
                .distinctBy { it.url }
            best.copy(result = best.result.copy(stream = best.result.stream.copy(subtitles = subtitles)))
        }
        val measured = ConcurrentLinkedQueue<MeasuredStream>()
        withTimeoutOrNull(PROBE_BUDGET_MS) {
            coroutineScope {
                unique.forEach { candidate ->
                    launch(Dispatchers.IO) {
                        mediaGate.withPermit {
                            ignoringFailure {
                                withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                                    speedProbe.bytesPerSecond(candidate.result.stream)
                                        ?.takeIf { it > 0 && it.isFinite() }
                                        ?.let { measured.add(MeasuredStream(candidate, it)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        coroutineContext.ensureActive()
        val healthy = measured.toList()
        val alternatives = healthy.filter { it.candidate.result.stream.url != failedStreamUrl }
        return alternatives.ifEmpty { healthy }.sortedWith(
            compareByDescending<MeasuredStream> { it.bytesPerSecond }
                .thenByDescending { it.candidate.result.stream.height ?: 0 }
                .thenBy { it.candidate.sourceIndex }
                .thenBy { it.candidate.serverIndex }
                .thenBy { it.candidate.variantIndex },
        ).firstOrNull()?.candidate?.result
            ?: error("No reachable stream for \"$title\", episode $episodeNumber")
    }

    private suspend fun ignoringFailure(block: suspend () -> Unit) {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* An unavailable source/server must not discard another's media. */ }
    }

    private data class Candidate(
        val result: RecoveredStream,
        val sourceIndex: Int,
        val serverIndex: Int,
        val variantIndex: Int,
    )
    private data class MeasuredStream(val candidate: Candidate, val bytesPerSecond: Double)

    private companion object {
        const val REGISTRY_WAIT_MS = 3_000L
        const val RESOLUTION_BUDGET_MS = 12_000L
        const val SOURCE_TIMEOUT_MS = 8_000L
        const val SERVER_TIMEOUT_MS = 4_000L
        const val PROBE_BUDGET_MS = 12_000L
        const val PROBE_TIMEOUT_MS = 5_000L
        const val MAX_MEDIA_PROBES = 3
        val candidateOrder = compareBy<Candidate>({ it.sourceIndex }, { it.serverIndex }, { it.variantIndex })
    }
}
