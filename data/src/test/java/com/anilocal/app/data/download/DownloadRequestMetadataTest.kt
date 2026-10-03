package com.anilocal.app.data.download

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class DownloadRequestMetadataTest {
    @Test fun sourceHeadersSurvivePersistedRequestDataRoundTrip() {
        val headers = mapOf(
            "Referer" to "https://source.example/episode?title=quoted\"title",
            "Authorization" to "Bearer source-token",
            "Cookie" to "session=abc; selected-language=en",
        )
        assertEquals(headers, DownloadRequestMetadata.headers(DownloadRequestMetadata.encode(headers)))
    }

    @Test fun existingEmptyOrOpaqueRequestDataUsesTheDefaultHeaderlessBehavior() {
        assertArrayEquals(byteArrayOf(), DownloadRequestMetadata.encode(emptyMap()))
        listOf("", "legacy opaque notes", "{broken", "[]", "null", "{\"unrelated\":{\"note\":\"old request\"}}")
            .forEach { assertTrue(DownloadRequestMetadata.headers(it.toByteArray()).isEmpty()) }
    }

    @Test fun unknownVersionsAndInvalidHeaderMetadataDoNotBreakExistingDownloads() {
        listOf(
            "{\"anilocal.requestHeaders.v2\":{\"Authorization\":\"new schema\"}}",
            "{\"anilocal.requestHeaders.v1\":null}",
            "{\"anilocal.requestHeaders.v1\":{\"Authorization\":null}}",
            "{\"anilocal.requestHeaders.v1\":{\"\":\"invalid header\"}}",
        ).forEach { assertTrue(DownloadRequestMetadata.headers(it.toByteArray()).isEmpty()) }
    }

    @Test fun mutableSourceHeadersCannotChangeAnAlreadyQueuedDownloadsHeaders() {
        val source = mutableMapOf("Authorization" to "Bearer first", "Referer" to "https://first.example")
        val first = DownloadRequestMetadata.encode(source)
        source["Authorization"] = "Bearer second"
        source["Referer"] = "https://second.example"
        val second = DownloadRequestMetadata.encode(source)
        assertEquals("Bearer first", DownloadRequestMetadata.headers(first)["Authorization"])
        assertEquals("https://first.example", DownloadRequestMetadata.headers(first)["Referer"])
        assertEquals(source, DownloadRequestMetadata.headers(second))
    }

    @Test fun concurrentDownloadsKeepIndependentAuthorizationAndRefererValues() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = executor.invokeAll((1..40).map { id -> Callable {
                val own = mapOf("Authorization" to "Bearer token-$id", "Referer" to "https://source-$id.example")
                own to DownloadRequestMetadata.headers(DownloadRequestMetadata.encode(own))
            } })
            results.forEach { result ->
                val (expected, actual) = result.get()
                assertEquals(expected, actual)
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
