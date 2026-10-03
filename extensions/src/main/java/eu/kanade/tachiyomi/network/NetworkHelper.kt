package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.IgnoreGzipInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Trimmed vendored NetworkHelper (see VENDORING.md). DoH providers, Brotli and HTTP-logging are
 * dropped (each drags in code the host omits), but the Cloudflare/WebView bypass IS wired back in
 * via [CloudflareInterceptor] (plain android.webkit, no androidx.webkit) so CF-gated sources can
 * resolve. Keeps the public surface that AnimeHttpSource and extensions reference: [client] /
 * [cloudflareClient] / [nonCloudflareClient] / [cookieJar] / [defaultUserAgentProvider].
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
        // After UserAgentInterceptor so it sees the applied User-Agent (cf_clearance is UA-bound and
        // the WebView must solve under the same UA the retried request will send).
        .addInterceptor(CloudflareInterceptor(context, cookieJar, ::defaultUserAgentProvider))
        .addNetworkInterceptor(IgnoreGzipInterceptor())
        .build()

    val nonCloudflareClient: OkHttpClient = client

    @Deprecated("The regular client handles Cloudflare by default")
    @Suppress("UNUSED")
    val cloudflareClient: OkHttpClient = client

    fun defaultUserAgentProvider(): String = preferences.defaultUserAgent()
}
