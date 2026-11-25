package com.anilocal.app.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * The app's one JSON configuration — for API responses, the JSON cache, and the JSON columns in the
 * downloads table. Serializers are generated at compile time (`@Serializable`), so there is no
 * reflection anywhere in parsing.
 *
 * Shaped to read and write exactly what the earlier Moshi setup did, so cached pages and downloaded
 * episodes' stored subtitles/skip markers/headers stay readable across the switch (and by older
 * builds): every field is written, defaults included, and nulls are omitted.
 */
val AppJson = Json {
    // APIs add fields over time, and a cached row may come from a model that had more.
    ignoreUnknownKeys = true
    // Nulls are omitted on write (as Moshi did), and a missing nullable field reads as null.
    explicitNulls = false
    // Write fields at their default too, so rows look the same as before to any reader.
    encodeDefaults = true
    // An explicit null or an unknown enum name for a field that has a default takes the default.
    coerceInputValues = true
    // MAL's load.json sends a number where a string is expected for some titles; Moshi accepted that.
    isLenient = true
}

/** [AppJson] bound to one type — for JSON stored in a database column (the downloads table). */
class JsonCodec<T>(private val serializer: KSerializer<T>) {
    fun toJson(value: T): String = AppJson.encodeToString(serializer, value)
    fun fromJson(json: String): T = AppJson.decodeFromString(serializer, json)
}
