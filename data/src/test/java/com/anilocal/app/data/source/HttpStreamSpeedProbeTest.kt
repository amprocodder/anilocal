package com.anilocal.app.data.source

import com.anilocal.app.domain.model.VideoStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class HttpStreamSpeedProbeTest {
    private val clients = mutableListOf<OkHttpClient>()

    @After fun closeClients() {
        clients.forEach {
            it.connectionPool.evictAll()
            it.dispatcher.executorService.shutdownNow()
        }
    }

    @Test fun directMediaReadsAreBoundedEvenWhenServerIgnoresRangeAndBodiesClose() = runBlocking {
        val requests = ConcurrentLinkedQueue<Request>()
        val bodies = ConcurrentLinkedQueue<TrackedBody>()
        val probe = probe { request, _ ->
            requests.add(request)
            response(request, TrackedBody(ByteArray(1_024 * 1_024)).also { bodies.add(it) })
        }
        val stream = VideoStream("https://media.test/video.mp4", headers = mapOf(
            "Referer" to "https://source.test/episode", "Authorization" to "test-token",
        ))

        assertNotNull(probe.bytesPerSecond(stream))
        assertNotNull(probe.bytesPerSecond(stream))
        assertEquals(2, requests.size)
        requests.forEach {
            assertEquals("bytes=0-262143", it.header("Range"))
            assertEquals("https://source.test/episode", it.header("Referer"))
            assertEquals("test-token", it.header("Authorization"))
            assertEquals("no-cache", it.header("Cache-Control"))
        }
        bodies.forEach {
            assertEquals(256 * 1_024L, it.bytesRead)
            assertTrue(it.closed)
        }
    }

    @Test fun hlsProbesActualMediaAndUsesEachRedirectedManifestAsRelativeUriBase() = runBlocking {
        val requests = ConcurrentLinkedQueue<Request>()
        val bodies = ConcurrentLinkedQueue<TrackedBody>()
        val probe = probe { request, _ ->
            requests.add(request)
            val body: TrackedBody
            val finalRequest: Request
            when (request.url.toString()) {
                "https://original.test/master.m3u8" -> {
                    body = TrackedBody("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=1280x720\nvariant.m3u8\n", "application/x-mpegURL")
                    finalRequest = request.newBuilder().url("https://redirected.test/catalog/master.m3u8").build()
                }
                "https://redirected.test/catalog/variant.m3u8" -> {
                    body = TrackedBody("#EXTM3U\n#EXTINF:5,\n../chunks/first.ts\n#EXT-X-ENDLIST\n", "application/x-mpegURL")
                    finalRequest = request.newBuilder().url("https://segments.test/alt/media.m3u8").build()
                }
                "https://segments.test/chunks/first.ts" -> {
                    body = TrackedBody(ByteArray(64 * 1_024), "video/mp2t")
                    finalRequest = request
                }
                else -> throw AssertionError("Unexpected URL ${request.url}")
            }
            bodies.add(body)
            response(finalRequest, body)
        }

        assertNotNull(probe.bytesPerSecond(VideoStream("https://original.test/master.m3u8", height = 720,
            headers = mapOf("Referer" to "https://source.test/episode"))))
        assertEquals(listOf(
            "https://original.test/master.m3u8",
            "https://redirected.test/catalog/variant.m3u8",
            "https://segments.test/chunks/first.ts",
        ), requests.map { it.url.toString() })
        assertTrue(requests.all { it.header("Referer") == "https://source.test/episode" })
        assertEquals("bytes=0-262143", requests.last().header("Range"))
        assertTrue(bodies.all { it.closed })
    }

    @Test fun aFastManifestCannotMakeAnUnavailableSegmentHealthy() = runBlocking {
        val bodies = ConcurrentLinkedQueue<TrackedBody>()
        val probe = probe { request, _ ->
            val playlist = request.url.encodedPath.endsWith(".m3u8")
            val body = if (playlist) TrackedBody("#EXTM3U\n#EXTINF:5,\nmedia.ts\n", "application/x-mpegURL")
                else TrackedBody(ByteArray(0))
            bodies.add(body)
            response(request, body, if (playlist) 200 else 503)
        }
        assertNull(probe.bytesPerSecond(VideoStream("https://media.test/main.m3u8")))
        assertEquals(2, bodies.size)
        assertTrue(bodies.all { it.closed })
    }

    @Test fun hlsByteRangeOffsetAndLengthArePreservedWithinMediaLimit() = runBlocking {
        val requests = ConcurrentLinkedQueue<Request>()
        val probe = probe { request, _ ->
            requests.add(request)
            response(request, if (request.url.encodedPath.endsWith(".m3u8")) {
                TrackedBody("#EXTM3U\n#EXTINF:5,\n#EXT-X-BYTERANGE:65536@4096\nmedia.ts\n", "application/x-mpegURL")
            } else TrackedBody(ByteArray(65_536)))
        }
        assertNotNull(probe.bytesPerSecond(VideoStream("https://media.test/main.m3u8")))
        assertEquals("bytes=4096-69631", requests.last().header("Range"))
    }

    @Test fun oversizedManifestAndHtmlResponseAreRejectedWithBodiesClosed() = runBlocking {
        val bodies = ConcurrentLinkedQueue<TrackedBody>()
        val probe = probe { request, _ ->
            val body = if (request.url.encodedPath.endsWith(".m3u8")) {
                TrackedBody(("#EXTM3U\n" + "# padding\n".repeat(20_000)), "application/x-mpegURL")
            } else TrackedBody("<html>".repeat(1_024), "text/html")
            bodies.add(body)
            response(request, body)
        }
        assertNull(probe.bytesPerSecond(VideoStream("https://media.test/main.m3u8")))
        assertNull(probe.bytesPerSecond(VideoStream("https://media.test/video.mp4")))
        assertTrue(bodies.first().bytesRead <= 64 * 1_024 + 8_192L)
        assertTrue(bodies.all { it.closed })
    }

    @Test fun cancellingWhileReadingClosesTheResponseAndCancelsTheActiveCall() = runBlocking {
        val reading = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val closed = AtomicBoolean(false)
        val activeCall = AtomicReference<Call>()
        val blockingSource = object : Source {
            override fun timeout() = Timeout.NONE
            override fun read(sink: Buffer, byteCount: Long): Long {
                reading.countDown()
                if (!releaseRead.await(2, TimeUnit.SECONDS)) throw IOException("Blocked test read")
                throw IOException("Body closed")
            }
            override fun close() {
                closed.set(true)
                releaseRead.countDown()
            }
        }.buffer()
        val body = object : ResponseBody() {
            override fun contentType() = "video/mp4".toMediaType()
            override fun contentLength() = -1L
            override fun source() = blockingSource
        }
        val probe = probe { request, call -> activeCall.set(call); response(request, body) }
        val operation = launch(Dispatchers.Default) {
            probe.bytesPerSecond(VideoStream("https://media.test/video.mp4"))
        }
        try {
            assertTrue("Probe must reach media body read", reading.await(2, TimeUnit.SECONDS))
            operation.cancelAndJoin()
            assertTrue(closed.get())
            assertTrue(activeCall.get().isCanceled())
        } finally {
            releaseRead.countDown()
            operation.cancelAndJoin()
        }
    }

    private fun probe(handler: (Request, Call) -> Response): HttpStreamSpeedProbe {
        val client = OkHttpClient.Builder().addInterceptor { chain -> handler(chain.request(), chain.call()) }.build()
        clients.add(client)
        return HttpStreamSpeedProbe(client)
    }

    private fun response(request: Request, body: ResponseBody, code: Int = 200) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test response").body(body).build()

    private class TrackedBody(bytes: ByteArray, private val type: String = "video/mp4") : ResponseBody() {
        constructor(text: String, type: String) : this(text.toByteArray(), type)
        var bytesRead = 0L
        var closed = false
        private val length = bytes.size.toLong()
        private val tracked: BufferedSource = object : ForwardingSource(Buffer().write(bytes)) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = super.read(sink, byteCount)
                if (read > 0) bytesRead += read
                return read
            }
            override fun close() { closed = true; super.close() }
        }.buffer()
        override fun contentType() = type.toMediaType()
        override fun contentLength() = length
        override fun source() = tracked
    }
}
