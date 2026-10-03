package com.anilocal.app.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector

/** Top-level tabs — the AniLab-style bottom menu. */
enum class TopTab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Outlined.Home),
    Explore("explore", "Explore", Icons.Outlined.Explore),
    Library("library", "My List", Icons.Outlined.VideoLibrary),
    Downloads("downloads", "Downloads", Icons.Outlined.Download),
    More("more", "More", Icons.Outlined.MoreHoriz),
}

object Routes {
    const val EXTENSIONS = "extensions"

    // Per-source preferences (ConfigurableAnimeSource). The source id ("aniyomi:<long>") is URL-encoded
    // into the path segment and decoded back by NavType.StringType into the screen's SavedStateHandle.
    const val SOURCE_PREFERENCES = "source/{sourceId}/preferences"
    fun sourcePreferences(sourceId: String) = "source/${Uri.encode(sourceId)}/preferences"

    const val DETAIL = "detail/{animeId}"
    fun detail(animeId: String) = "detail/$animeId"

    // startMs is an optional resume position (ms); omitted callers default to 0 (start from the beginning).
    const val PLAYER = "player/{animeId}/{episodeNumber}?startMs={startMs}"
    fun player(animeId: String, episodeNumber: Int, startMs: Long = 0L) =
        "player/$animeId/$episodeNumber?startMs=$startMs"
}
