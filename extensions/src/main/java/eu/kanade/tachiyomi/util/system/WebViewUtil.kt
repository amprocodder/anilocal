package eu.kanade.tachiyomi.util.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Trimmed vendored copy of Aniyomi's WebViewUtil (see VENDORING.md). Only the members the Cloudflare
 * interceptor needs are kept: [WebViewUtil.supportsWebView] (the no-op safety gate), and the
 * [WebView.setDefaultSettings] / [WebView.isOutdated] extensions. moko-resources/logcat are dropped
 * in favour of android.util.Log; no androidx.webkit is used.
 */
object WebViewUtil {
    const val MINIMUM_WEBVIEW_VERSION = 118

    /**
     * Returns false (so the caller no-ops) when no usable WebView is present. Touching
     * [CookieManager.getInstance] can throw when WebView isn't installed/updating
     * (android.webkit.WebViewFactory$MissingWebViewPackageException), so guard it.
     */
    fun supportsWebView(context: Context): Boolean {
        return try {
            CookieManager.getInstance()
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
        } catch (e: Throwable) {
            Log.e("WebViewUtil", "WebView unavailable", e)
            false
        }
    }
}

fun WebView.isOutdated(): Boolean = getWebViewMajorVersion() < WebViewUtil.MINIMUM_WEBVIEW_VERSION

@SuppressLint("SetJavaScriptEnabled")
fun WebView.setDefaultSettings() {
    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        cacheMode = WebSettings.LOAD_DEFAULT
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
    }
    CookieManager.getInstance().acceptThirdPartyCookies(this)
}

private fun WebView.getWebViewMajorVersion(): Int {
    val uaRegexMatch = """.*Chrome/(\d+)\..*""".toRegex().matchEntire(getDefaultUserAgentString())
    return if (uaRegexMatch != null && uaRegexMatch.groupValues.size > 1) {
        uaRegexMatch.groupValues[1].toInt()
    } else {
        0
    }
}

// Based on https://stackoverflow.com/a/29218966 — temporarily null the UA to read the device
// default, then restore it. Must be called on the main thread (touches a live WebView's settings).
private fun WebView.getDefaultUserAgentString(): String {
    val originalUA: String = settings.userAgentString
    settings.userAgentString = null
    val defaultUserAgentString = settings.userAgentString
    settings.userAgentString = originalUA
    return defaultUserAgentString
}

/**
 * Minimal DeviceUtil: only the two members the interceptor's eager-init guard needs. Skipping eager
 * WebView init on MIUI and Samsung/Android-12 avoids a known Chromium crash (crbug.com/1279562).
 */
object DeviceUtil {
    val isSamsung by lazy { Build.MANUFACTURER.equals("samsung", ignoreCase = true) }

    val isMiui by lazy { getSystemProperty("ro.miui.ui.version.name")?.isNotEmpty() ?: false }

    @SuppressLint("PrivateApi")
    private fun getSystemProperty(key: String): String? = try {
        Class.forName("android.os.SystemProperties")
            .getDeclaredMethod("get", String::class.java)
            .invoke(null, key) as? String
    } catch (e: Exception) {
        null
    }
}
