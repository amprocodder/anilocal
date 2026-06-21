package com.anilocal.app.ui.navigation

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
    Library("library", "Library", Icons.Outlined.VideoLibrary),
    Downloads("downloads", "Downloads", Icons.Outlined.Download),
    More("more", "More", Icons.Outlined.MoreHoriz),
}

object Routes {
    const val DETAIL = "detail/{animeId}"
    fun detail(animeId: String) = "detail/$animeId"

    const val PLAYER = "player/{animeId}/{episodeNumber}"
    fun player(animeId: String, episodeNumber: Int) = "player/$animeId/$episodeNumber"
}
