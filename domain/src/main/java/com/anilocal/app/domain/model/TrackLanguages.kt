package com.anilocal.app.domain.model

import java.util.Locale

/**
 * Track languages, as the subtitle/audio settings and the player need them. Sources name their tracks
 * and variants in free text — "English", "eng", "Spanish - Latino", "Portuguese (Brazil)",
 * "English [CC]", "HD-1 Dub 1080p" — so a language is recognised loosely: by its English or native
 * name or its ISO 639-2 code as a whole word, or by its ISO 639-1 code only when nothing longer
 * names a language (two letters are too easily an ordinary word: "de" in "Español de España").
 */
object TrackLanguages {
    /** The setting's "decide for me" value: the device's language for subtitles, the source's choice for audio. */
    const val AUTO = ""

    /** Subtitle setting: start every episode with captions hidden. */
    const val OFF = "off"

    const val ENGLISH = "en"

    /** Anime's original audio, and the audio setting's default. */
    const val JAPANESE = "ja"

    /** The languages Settings offers, as ISO 639-1 codes: English and Japanese, then by name. */
    val choices: List<String> = listOf(
        "en", "ja", "ar", "zh", "nl", "fr", "de", "hi", "id", "it", "ko", "ms", "pl", "pt", "ru", "es",
        "th", "tr", "vi",
    )

    fun displayName(code: String): String =
        Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code }

    /**
     * Subtitle languages to look for, best first: the setting's own language (unless it's [AUTO] or
     * [OFF]), then the device's, then English — so an episode without the chosen language still
     * starts in one the user reads rather than whatever the source lists first.
     */
    fun subtitleOrder(preferred: String, device: Locale = Locale.getDefault()): List<String> =
        listOf(preferred.takeUnless { it == AUTO || it == OFF }, deviceLanguage(device), ENGLISH)
            .filterNotNull().map { it.lowercase(Locale.ROOT) }.distinct()

    /** The device's language as an ISO 639-1 code. Android's [Locale.getLanguage] can still report
     *  the withdrawn codes (Indonesian "in", Hebrew "iw", Yiddish "ji", as in `values-in`), which
     *  name no track here — Indonesian captions would lose to English. */
    private fun deviceLanguage(device: Locale): String? =
        when (val code = device.language.lowercase(Locale.ROOT)) {
            "" -> null
            "in" -> "id"
            "iw" -> "he"
            "ji" -> "yi"
            else -> code
        }

    /**
     * The language [text] names, among [among] (ISO 639-1 codes), or null when it names none of them.
     * A text that is itself a language tag ("en", "eng", "pt-BR") is read as one; otherwise the
     * earliest name or 3-letter code in it wins, and a bare 2-letter code counts only when no name does.
     */
    fun identify(text: String, among: Collection<String> = choices): String? {
        val lower = text.trim().lowercase(Locale.ROOT)
        if (lower.isEmpty()) return null
        TAG.matchEntire(lower)?.let { tag ->
            val primary = tag.groupValues[1]
            among.firstOrNull { code -> code == primary || primary in codes3(code) }?.let { return it }
        }
        var best: String? = null
        var bestAt = Int.MAX_VALUE
        for (code in among) {
            val at = (names(code) + codes3(code)).minOfOrNull { wordIndex(lower, it) ?: Int.MAX_VALUE }
                ?: Int.MAX_VALUE
            if (at < bestAt) { best = code; bestAt = at }
        }
        if (best != null) return best
        return among.mapNotNull { code -> wordIndex(lower, code)?.let { code to it } }.minByOrNull { it.second }?.first
    }

    /**
     * How well a stream variant's [label] fits the [preferred] audio language: higher is better, 1 when
     * the label doesn't say (or the preference is [AUTO]). Sources that offer both versions of an
     * episode tell them apart only in the label — "Sub"/"Dub", "jpn"/"eng", "Latino" — and that's the
     * only place this looks: a muxed stream's own audio tracks are chosen by the player.
     */
    fun audioFit(label: String?, preferred: String): Int {
        if (preferred == AUTO || label.isNullOrBlank()) return 1
        val lower = label.lowercase(Locale.ROOT)
        val audio = when {
            DUB_WORDS.any { wordIndex(lower, it) != null } ->
                identify(lower)?.takeIf { it != JAPANESE } ?: DUBBED
            ORIGINAL_WORDS.any { wordIndex(lower, it) != null } -> JAPANESE
            else -> identify(lower)
        } ?: return 1
        return when {
            audio == preferred -> 3
            preferred == JAPANESE -> 0
            audio == DUBBED -> if (preferred == ENGLISH) 2 else 1   // a bare "Dub" is usually English
            else -> 0
        }
    }

    private const val DUBBED = "dub"
    private val DUB_WORDS = listOf("dub", "dubbed", "dublado", "doblado", "doblaje")
    private val ORIGINAL_WORDS = listOf(
        "sub", "subs", "subbed", "subtitled", "subtitles", "softsub", "hardsub", "raw", "original",
        "legendado", "subtitulado", "vostfr",
    )

    private val TAG = Regex("([a-z]{2,3})(?:[-_][a-z0-9]{1,8})*")

    // ISO 639-2 bibliographic codes that differ from Java's terminology ones; sources use both.
    private val BIBLIOGRAPHIC = mapOf("fr" to "fre", "de" to "ger", "zh" to "chi", "nl" to "dut")

    private val ALIASES = mapOf(
        "ja" to listOf("jp"),
        "es" to listOf("latino", "castellano"),
        "pt" to listOf("brazilian"),
        "zh" to listOf("mandarin", "cantonese"),
    )

    private val nameCache = HashMap<String, List<String>>()

    private fun names(code: String): List<String> = synchronized(nameCache) {
        nameCache.getOrPut(code) {
            val locale = Locale.forLanguageTag(code)
            (listOf(locale.getDisplayLanguage(Locale.ENGLISH), locale.getDisplayLanguage(locale)) + ALIASES[code].orEmpty())
                .map { it.lowercase(Locale.ROOT) }.filter { it.isNotBlank() && it != code }.distinct()
        }
    }

    private fun codes3(code: String): List<String> =
        listOfNotNull(runCatching { Locale.forLanguageTag(code).isO3Language }.getOrNull(), BIBLIOGRAPHIC[code])
            .filter { it.length == 3 }

    /** Where [word] first occurs in [text] as a whole word, or null. Scripts written without spaces
     *  (CJK, Thai) match anywhere: "中文" inside "简体中文". */
    private fun wordIndex(text: String, word: String): Int? {
        val spaced = word.none { Character.UnicodeScript.of(it.code) in UNSPACED }
        var from = 0
        while (true) {
            val at = text.indexOf(word, from)
            if (at < 0) return null
            val end = at + word.length
            // Digits bound a word too: "th" isn't Thai in "4th", nor "ms" Malay in "30ms".
            if (!spaced || ((at == 0 || !text[at - 1].isLetterOrDigit()) &&
                    (end == text.length || !text[end].isLetterOrDigit()))) {
                return at
            }
            from = at + 1
        }
    }

    private val UNSPACED = setOf(
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.THAI, Character.UnicodeScript.LAO, Character.UnicodeScript.KHMER,
        Character.UnicodeScript.MYANMAR,
    )
}
