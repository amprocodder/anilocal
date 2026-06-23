package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.IgnoreGzipInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Trimmed vendored NetworkHelper (see VENDORING.md). Upstream also wires a Cloudflare/WebView
 * interceptor, DoH providers, Brotli and HTTP-logging — all dropped here: none are on the
 * stream-resolve path and each drags in code the host deliberately omits (androidx.webkit,
 * okhttp-brotli, a preference framework). Keeps the public surface that AnimeHttpSource and
 * extensions reference: [client] / [cloudflareClient] / [nonCloudflareClient] / [cookieJar] /
 * [defaultUserAgentProvider]. Cloudflare-gated sources simply fail to resolve for now, which the
 * app surfaces as a short hint (its silent-degrade convention); re-add a WebView interceptor later.
 */
class NetworkHelper(
    context: Context,
    private val preferences: NetworkPreferences,
) {
    val cookieJar = AndroidCookieJar()

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .cache(
            Cache(
                directory = File(context.cacheDir, "network_cache"),
                maxSize = 5L * 1024 * 1024, // 5 MiB
            ),
        )
        .addInterceptor(UncaughtExceptionInterceptor())
        .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
        .addNetworkInterceptor(IgnoreGzipInterceptor())
        .build()

    val nonCloudflareClient: OkHttpClient = client

    @Deprecated("The regular client handles Cloudflare by default")
    @Suppress("UNUSED")
    val cloudflareClient: OkHttpClient = client

    fun defaultUserAgentProvider(): String = preferences.defaultUserAgent()
}
