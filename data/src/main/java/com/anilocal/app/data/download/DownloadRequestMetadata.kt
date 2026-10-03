package com.anilocal.app.data.download

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/** Media3 persists request.data across service/process restarts; Room's offline format stays intact. */
internal object DownloadRequestMetadata {
    private const val HEADER_KEY = "anilocal.requestHeaders.v1"
    private val headerType = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    private val metadataType = Types.newParameterizedType(Map::class.java, String::class.java, headerType)
    private val adapter = Moshi.Builder().build()
        .adapter<Map<String, Map<String, String?>>>(metadataType)

    fun encode(headers: Map<String, String>): ByteArray =
        if (headers.isEmpty()) byteArrayOf()
        else adapter.toJson(mapOf(HEADER_KEY to headers)).toByteArray(Charsets.UTF_8)

    fun headers(data: ByteArray): Map<String, String> {
        if (data.isEmpty()) return emptyMap()
        return try {
            val values = adapter.fromJson(data.toString(Charsets.UTF_8))?.get(HEADER_KEY)
                ?: return emptyMap()
            if (values.any { (name, value) -> name.isBlank() || value == null }) return emptyMap()
            values.mapValues { checkNotNull(it.value) }
        } catch (_: Exception) {
            // Existing requests have empty/opaque data; malformed or unknown metadata stays compatible.
            emptyMap()
        }
    }
}
