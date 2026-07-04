package com.anilocal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.anilocal.app.ui.common.ContinueWatchingBar
import com.anilocal.app.ui.details.DetailsScreen
import com.anilocal.app.ui.downloads.DownloadsScreen
import com.anilocal.app.ui.explore.ExploreScreen
import com.anilocal.app.ui.extensions.ExtensionsScreen
import com.anilocal.app.ui.home.HomeScreen
import com.anilocal.app.ui.library.LibraryScreen
import com.anilocal.app.ui.more.MoreScreen
import com.anilocal.app.ui.navigation.Routes
import com.anilocal.app.ui.navigation.TopTab
import com.anilocal.app.ui.player.PlayerScreen
import com.anilocal.app.ui.source.SourcePreferencesScreen
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
    val resumeItem by appVm.resumeBar.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = TopTab.entries.any { it.route == currentRoute }
    // The player is fullscreen/immersive — it must bleed under where the system bars were, so it gets
    // no Scaffold content padding (deterministic, vs. relying on bar-hiding to collapse the insets).
    val isPlayer = currentRoute == Routes.PLAYER

    // Vertical insets are handled per-screen so edge-to-edge art (Home hero, Details header) can
    // draw under the status bar while other screens use statusBarsPadding() (or their own
    // Scaffold). Horizontal bars/cutouts (landscape 3-button nav, corner cutouts) stay padded here
    // for every route — no screen wants content under those.
    Scaffold(
        contentWindowInsets = WindowInsets.systemBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
        bottomBar = {
            if (showBottomBar) {
                Column {
                    resumeItem?.let { item ->
                        ContinueWatchingBar(
                            item = item,
                            onResume = {
                                nav.navigate(Routes.player(item.anime.id, item.episodeNumber, item.positionMs))
                            },
                            onDismiss = appVm::dismissBar,
                        )
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
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
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.tertiary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = TopTab.Home.route,
            modifier = if (isPlayer) Modifier else Modifier.padding(padding),
        ) {
            composable(TopTab.Home.route) {
                HomeScreen(
                    onOpen = { nav.navigate(Routes.detail(it)) },
                    onResume = { id, ep, pos -> nav.navigate(Routes.player(id, ep, pos)) },
                    onExplore = {
                        nav.navigate(TopTab.Explore.route) {
                            popUpTo(TopTab.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
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
            composable(TopTab.More.route) {
                MoreScreen(
                    onBrowseExtensions = { nav.navigate(Routes.EXTENSIONS) },
                    onConfigureSource = { nav.navigate(Routes.sourcePreferences(it)) },
                )
            }
            composable(Routes.EXTENSIONS) {
                ExtensionsScreen(onBack = { nav.popBackStack() })
            }
            composable(
                Routes.SOURCE_PREFERENCES,
                arguments = listOf(navArgument("sourceId") { type = NavType.StringType }),
            ) {
                SourcePreferencesScreen(onBack = { nav.popBackStack() })
            }

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
                    navArgument("startMs") { type = NavType.LongType; defaultValue = 0L },
                ),
                // Instant cut in/out — the video SurfaceView can't alpha-fade with the Compose chrome,
                // so a default fade desyncs (UI fades while the frame lingers). No transition = no desync.
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                PlayerScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
