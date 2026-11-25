package com.anilocal.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException

/** A non-2xx response; [code] is the HTTP status, for callers that treat one as an answer (AniSkip's 404). */
class HttpException(val code: Int, message: String) : IOException("HTTP $code $message".trim())

/**
 * Run the call on OkHttp's dispatcher and suspend until it answers — no thread blocked while waiting.
 * Cancelling the coroutine cancels the call; a response that arrives after cancellation is closed.
 */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, value, _ -> value.close() }
        }
    })
}

/**
 * JSON over HTTP for the app's own APIs (AniList, AniSkip, MAL, extension repos) — each endpoint is a
 * few lines on top of this, so no REST framework is needed. Bodies are decoded straight from the
 * response stream with the app-wide [AppJson] and a compile-time serializer.
 */
@Singleton
class JsonHttp @Inject constructor(private val client: OkHttpClient) {

    suspend fun <T> get(
        url: HttpUrl,
        deserializer: DeserializationStrategy<T>,
        headers: Map<String, String> = emptyMap(),
    ): T = execute(request(url, headers).get().build(), deserializer)

    suspend fun <T> post(
        url: HttpUrl,
        body: JsonElement,
        deserializer: DeserializationStrategy<T>,
        headers: Map<String, String> = emptyMap(),
    ): T = execute(
        request(url, headers).post(AppJson.encodeToString(JsonElement.serializer(), body).toRequestBody(JSON)).build(),
        deserializer,
    )

    private fun request(url: HttpUrl, headers: Map<String, String>) = Request.Builder().url(url).apply {
        header("Accept", "application/json")
        headers.forEach { (name, value) -> header(name, value) }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun <T> execute(request: Request, deserializer: DeserializationStrategy<T>): T {
        val response = client.newCall(request).await()
        // Reading the body blocks, so decode on the IO pool whatever dispatcher the caller is on. The
        // close sits outside withContext so a cancellation before the block runs can't leak the body.
        try {
            return withContext(Dispatchers.IO) {
                if (!response.isSuccessful) throw HttpException(response.code, response.message)
                AppJson.decodeFromBufferedSource(deserializer, response.body.source())
            }
        } finally {
            response.close()
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
