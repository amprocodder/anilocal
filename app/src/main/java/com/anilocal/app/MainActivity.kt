package com.anilocal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.anilocal.app.ui.details.DetailsScreen
import com.anilocal.app.ui.downloads.DownloadsScreen
import com.anilocal.app.ui.explore.ExploreScreen
import com.anilocal.app.ui.home.HomeScreen
import com.anilocal.app.ui.library.LibraryScreen
import com.anilocal.app.ui.more.MoreScreen
import com.anilocal.app.ui.navigation.Routes
import com.anilocal.app.ui.navigation.TopTab
import com.anilocal.app.ui.player.PlayerScreen
import com.anilocal.app.ui.theme.AniLocalTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AniLocalTheme { AppRoot() } }
    }
}

@Composable
private fun AppRoot() {
    val appVm: AppViewModel = hiltViewModel()
    LaunchedEffect(Unit) { appVm.onAppOpen() }   // throttled MAL sync on app open
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = TopTab.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(TopTab.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { androidx.compose.material3.Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = TopTab.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopTab.Home.route) {
                HomeScreen(onOpen = { nav.navigate(Routes.detail(it)) })
            }
            composable(TopTab.Explore.route) {
                ExploreScreen(onOpen = { nav.navigate(Routes.detail(it)) })
            }
            composable(TopTab.Library.route) {
                LibraryScreen(onOpen = { nav.navigate(Routes.detail(it)) })
            }
            composable(TopTab.Downloads.route) {
                DownloadsScreen(onPlay = { animeId, ep -> nav.navigate(Routes.player(animeId, ep)) })
            }
            composable(TopTab.More.route) { MoreScreen() }

            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("animeId") { type = NavType.StringType }),
            ) {
                DetailsScreen(
                    onPlay = { animeId, ep -> nav.navigate(Routes.player(animeId, ep)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                Routes.PLAYER,
                arguments = listOf(
                    navArgument("animeId") { type = NavType.StringType },
                    navArgument("episodeNumber") { type = NavType.IntType },
                ),
            ) {
                PlayerScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
