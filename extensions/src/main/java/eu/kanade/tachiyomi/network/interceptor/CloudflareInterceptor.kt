package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.isOutdated
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * Trimmed vendored copy of Aniyomi's CloudflareInterceptor (see VENDORING.md). Solves the Cloudflare
 * interstitial in a headless WebView and lets the shared [AndroidCookieJar] re-supply the fresh
 * cf_clearance cookie on the retry. Adapted to AniLocal: no androidx.webkit / moko-resources; on
 * failure it throws an [IOException] (the only thing OkHttp's async path tolerates), which the host
 * adapter's runCatching turns into a silent degrade rather than a crash.
 *
 * Detection deviates slightly from upstream for correctness: upstream sets `challengeFound` in
 * `onReceivedError` via `error.errorCode in [403,503]`, but [WebResourceError.errorCode] only holds
 * `WebViewClient.ERROR_*` constants (never an HTTP status), so that never fires for a real HTTP
 * challenge. We also override [WebViewClient.onReceivedHttpError] (HTTP `statusCode`), which is what
 * actually arrives for a 403/503 challenge — preventing a premature latch abort on the first
 * `onPageFinished` of the challenge page.
 */
class CloudflareInterceptor(
    context: Context,
    private val cookieManager: AndroidCookieJar,
    defaultUserAgentProvider: () -> String,
) : WebViewInterceptor(context, defaultUserAgentProvider) {

    override fun shouldIntercept(response: Response): Boolean {
        // Cloudflare anti-bot is on: 403/503 with a cloudflare Server header.
        return response.code in ERROR_CODES && response.header("Server") in SERVER_CHECK
    }

    override fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response {
        // Release the original body before the WebView work and the re-request.
        response.close()
        // The cf_clearance in play when this request 403'd — the stale one that just failed.
        val staleCookie = cookieManager.get(request.url).firstOrNull { it.name == "cf_clearance" }
        return try {
            // Serialize the cookie-delete + headless-WebView solve process-wide (a top-level lock, so
            // one at a time regardless of how many sources race): the WebView solves one challenge at
            // a time anyway, and two threads deleting/re-reading cf_clearance for the same host race —
            // one can wipe the cookie the other just earned. Auto mode racing several sources makes
            // this collision, previously latent, routine.
            cfLock.lock()
            try {
                // A losing race candidate that was cancelled while queued behind the lock must not
                // then spend up to 30s driving the WebView for a result nobody will use.
                if (chain.call().isCanceled()) throw IOException("Cancelled before Cloudflare bypass")
                // Another thread may have solved this host while we waited for the lock. If a
                // cf_clearance different from the stale one now exists, skip the solve and just retry.
                val current = cookieManager.get(request.url).firstOrNull { it.name == "cf_clearance" }
                if (current == null || current == staleCookie) {
                    cookieManager.remove(request.url, COOKIE_NAMES, 0)
                    val oldCookie = cookieManager.get(request.url).firstOrNull { it.name == "cf_clearance" }
                    resolveWithWebView(request, oldCookie)
                }
            } finally {
                cfLock.unlock()
            }

            // cookieJar now holds the fresh cf_clearance; loadForRequest re-supplies it on the retry.
            chain.proceed(request)
        } catch (e: CloudflareBypassException) {
            // OkHttp's enqueue() only tolerates IOException; wrap so the host degrades, not crashes.
            throw IOException("Failed to bypass Cloudflare", e)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(originalRequest: Request, oldCookie: Cookie?) {
        // OkHttp has no async interceptors, so block this background thread until the WebView (on the
        // main thread) solves the challenge. CountDownLatch establishes happens-before from each
        // countDown() to await()'s return, publishing the flags written before it.
        val latch = CountDownLatch(1)

        var webView: WebView? = null
        var challengeFound = false
        var cloudflareBypassed = false
        var isWebViewOutdated = false

        val origRequestUrl = originalRequest.url.toString()
        val headers = parseHeaders(originalRequest.headers)

        runOnMain {
            val view = try {
                createWebView(originalRequest)
            } catch (e: Throwable) {
                // Some OEMs reject a headless WebView built from a non-UI context: give up cleanly.
                latch.countDown()
                return@runOnMain
            }
            webView = view

            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    fun isCloudFlareBypassed(): Boolean =
                        cookieManager.get(origRequestUrl.toHttpUrl())
                            .firstOrNull { it.name == "cf_clearance" }
                            .let { it != null && it != oldCookie }

                    if (isCloudFlareBypassed()) {
                        cloudflareBypassed = true
                        latch.countDown()
                    }

                    // First load of the original URL with no challenge seen → nothing to solve, abort.
                    if (url == origRequestUrl && !challengeFound) {
                        latch.countDown()
                    }
                }

                // Network-level errors (errorCode is a WebViewClient.ERROR_* constant, never an HTTP
                // status). Kept for parity with upstream: a real main-frame network error aborts.
                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame && error.errorCode !in ERROR_CODES) {
                        latch.countDown()
                    }
                }

                // The path that actually fires for a 403/503 challenge: mark it found so the
                // onPageFinished abort above doesn't trip before the JS challenge completes.
                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    if (request.isForMainFrame && errorResponse.statusCode in ERROR_CODES) {
                        challengeFound = true
                    }
                }
            }

            view.loadUrl(origRequestUrl, headers)
        }

        latch.await(30, TimeUnit.SECONDS)

        runOnMain {
            if (!cloudflareBypassed) {
                isWebViewOutdated = webView?.isOutdated() == true
            }
            webView?.run {
                stopLoading()
                destroy()
            }
        }

        if (!cloudflareBypassed) {
            if (isWebViewOutdated) {
                Log.w("CloudflareInterceptor", "Cloudflare bypass failed; WebView may be outdated")
            }
            throw CloudflareBypassException()
        }
    }
}

private val ERROR_CODES = listOf(403, 503)
private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
private val COOKIE_NAMES = listOf("cf_clearance")

/** Process-wide: at most one Cloudflare solve (and its cookie delete/re-read) runs at a time, across
 *  every source and every racing candidate. See the deviation note in extensions/VENDORING.md. */
private val cfLock = ReentrantLock()

private class CloudflareBypassException : Exception()
