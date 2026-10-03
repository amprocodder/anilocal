package eu.kanade.tachiyomi.network

/**
 * Trimmed vendored NetworkPreferences (see VENDORING.md). Upstream backs each value on a
 * `PreferenceStore` from `tachiyomi.core.common.preference`; the host doesn't vendor that
 * framework, so this returns plain values. Only [defaultUserAgent] is consumed by the trimmed
 * [NetworkHelper]; [verboseLogging] is kept for API shape.
 */
class NetworkPreferences(
    private val verboseLogging: Boolean = false,
) {
    fun verboseLogging(): Boolean = verboseLogging

    fun defaultUserAgent(): String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0"
}
