package eu.kanade.tachiyomi.animesource.model

// Upstream annotates this @androidx.compose.runtime.Stable; dropped to keep Compose out of
// :extensions (the annotation is a no-op outside Compose UI).
data class AnimeFilterList(val list: List<AnimeFilter<*>>) : List<AnimeFilter<*>> by list {

    constructor(vararg fs: AnimeFilter<*>) : this(if (fs.isNotEmpty()) fs.asList() else emptyList())

    override fun equals(other: Any?): Boolean {
        return false
    }

    override fun hashCode(): Int {
        return list.hashCode()
    }
}
