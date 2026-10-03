package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.Locale

/**
 * Trimmed vendored copy of Aniyomi's WebViewInterceptor (see VENDORING.md). Main-thread scheduling
 * goes through a [Handler] on the main looper (no androidx.core executor); logging/i18n are dropped.
 * The OkHttp call always runs on a background dispatcher thread, so a subclass blocking on a latch
 * never deadlocks the UI. When no WebView is available the request silently no-ops (returns the
 * original, still-gated response) per the app's silent-degrade convention.
 */
abstract class WebViewInterceptor(
    private val context: Context,
    private val defaultUserAgentProvider: () -> String,
) : Interceptor {

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Warms WebView up the first time a challenge is hit, off the hot path. Skipped on devices known
     * to crash during this init (MIUI; Samsung on Android 12 — crbug.com/1279562).
     */
    private val initWebView by lazy {
        if (DeviceUtil.isMiui ||
            (Build.VERSION.SDK_INT == Build.VERSION_CODES.S && DeviceUtil.isSamsung)
        ) {
            return@lazy
        }
        try {
            WebSettings.getDefaultUserAgent(context)
        } catch (_: Exception) {
            // Chrome/WebView may be mid-update; ignore.
        }
    }

    abstract fun shouldIntercept(response: Response): Boolean

    abstract fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!shouldIntercept(response)) {
            return response
        }
        // No usable WebView → silently return the original (gated) response; the source then fails to
        // resolve and the player shows its short hint (no dialogs/toasts here, per convention).
        if (!WebViewUtil.supportsWebView(context)) {
            return response
        }
        initWebView
        return intercept(chain, request, response)
    }

    fun parseHeaders(headers: Headers): Map<String, String> {
        return headers
            // Unsafe headers make WebView throw net::ERR_INVALID_ARGUMENT.
            .filter { (name, value) -> isRequestHeaderSafe(name, value) }
            .groupBy(keySelector = { (name, _) -> name }) { (_, value) -> value }
            .mapValues { it.value.getOrNull(0).orEmpty() }
    }

    /** Posts [action] to the main thread (WebView APIs are main-thread-only). */
    fun runOnMain(action: () -> Unit) {
        handler.post(action)
    }

    fun createWebView(request: Request): WebView {
        return WebView(context).apply {
            setDefaultSettings()
            // Avoid an empty User-Agent (WebView would reset it to its default). cf_clearance is
            // UA-bound, so this must match what UserAgentInterceptor sends on the retry.
            settings.userAgentString = request.header("User-Agent") ?: defaultUserAgentProvider()
        }
    }
}

// Based on IsRequestHeaderSafe in Chromium's services/network/public/cpp/header_util.cc
private fun isRequestHeaderSafe(name0: String, value0: String): Boolean {
    val name = name0.lowercase(Locale.ENGLISH)
    val value = value0.lowercase(Locale.ENGLISH)
    if (name in unsafeHeaderNames || name.startsWith("proxy-")) return false
    if (name == "connection" && value == "upgrade") return false
    return true
}

private val unsafeHeaderNames = listOf(
    "content-length",
    "host",
    "trailer",
    "te",
    "upgrade",
    "cookie2",
    "keep-alive",
    "transfer-encoding",
    "set-cookie",
)
