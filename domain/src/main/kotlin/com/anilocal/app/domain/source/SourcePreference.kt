package com.anilocal.app.domain.source

/**
 * A user-configurable preference a source exposes, mapped from the extension's androidx
 * `PreferenceScreen` into a UI-agnostic model so `:app` can render it without ever seeing
 * `androidx.preference` or `eu.kanade.*` types (the boundary stays in `:extensions`). Maps the four
 * preference kinds Aniyomi anime extensions use; anything else is dropped. Persisted back through
 * [AnimeSource.setPreference], which writes the value the source reads via `getSourcePreferences()`.
 */
sealed interface SourcePreference {
    /** Persistence key (also the SharedPreferences key the source reads). */
    val key: String
    val title: String
    val summary: String?

    /** SwitchPreferenceCompat / CheckBoxPreference → a Boolean. */
    data class Toggle(
        override val key: String,
        override val title: String,
        override val summary: String?,
        val value: Boolean,
    ) : SourcePreference

    /** EditTextPreference → a free-text String. */
    data class EditText(
        override val key: String,
        override val title: String,
        override val summary: String?,
        val value: String,
    ) : SourcePreference

    /** ListPreference → one [value] chosen from [entryValues] (labelled by the parallel [entries]). */
    data class Select(
        override val key: String,
        override val title: String,
        override val summary: String?,
        val value: String,
        val entries: List<String>,
        val entryValues: List<String>,
    ) : SourcePreference

    /** MultiSelectListPreference → a set of [values] chosen from [entryValues]. */
    data class MultiSelect(
        override val key: String,
        override val title: String,
        override val summary: String?,
        val values: Set<String>,
        val entries: List<String>,
        val entryValues: List<String>,
    ) : SourcePreference
}
