package eu.kanade.tachiyomi.animesource

// Flattened from upstream's KMP expect/actual pair (commonMain `expect class PreferenceScreen` +
// androidMain `actual typealias … = androidx.preference.PreferenceScreen`) into a single typealias,
// since :extensions is a plain Android library (no commonMain/androidMain split). Referenced
// unqualified by ConfigurableAnimeSource.setupPreferenceScreen.
typealias PreferenceScreen = androidx.preference.PreferenceScreen
