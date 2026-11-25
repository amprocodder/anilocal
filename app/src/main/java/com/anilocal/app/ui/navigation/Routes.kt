package com.anilocal.app.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.anilocal.app.ui.icons.AppIcons
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Every screen the app can show, with its arguments as typed fields — no route strings to build or
 * parse. Serializable so the back stacks survive process death (see [NavState]).
 */
@Serializable
sealed interface Route {
    // Tab roots.
    @Serializable @SerialName("home") data object Home : Route
    @Serializable @SerialName("explore") data object Explore : Route
    @Serializable @SerialName("library") data object Library : Route
    @Serializable @SerialName("downloads") data object Downloads : Route
    @Serializable @SerialName("settings") data object Settings : Route

    /** A title's details page; keyed by AniList id. */
    @Serializable @SerialName("detail") data class Detail(val animeId: String) : Route

    /** The player. [episode] tracks the episode on screen (auto-advance updates it), so a restored
     *  stack reopens the episode that was playing; [startMs] is the initial resume position. */
    @Serializable @SerialName("player")
    data class Player(val animeId: String, val episode: Int, val startMs: Long = 0L) : Route

    /** Browse extension repos / install sources. */
    @Serializable @SerialName("extensions") data object Extensions : Route

    /** One source's own settings (`ConfigurableAnimeSource`). */
    @Serializable @SerialName("source-preferences") data class SourcePreferences(val sourceId: String) : Route
}

/** The bottom-bar tabs; each keeps its own back stack (see [Navigator]). */
enum class TopTab(val root: Route, val label: String, val icon: ImageVector) {
    Home(Route.Home, "Home", AppIcons.Outlined.Home),
    Explore(Route.Explore, "Explore", AppIcons.Outlined.Explore),
    Library(Route.Library, "My List", AppIcons.Outlined.VideoLibrary),
    Downloads(Route.Downloads, "Downloads", AppIcons.Outlined.Download),
    Settings(Route.Settings, "Settings", AppIcons.Outlined.Settings),
}
