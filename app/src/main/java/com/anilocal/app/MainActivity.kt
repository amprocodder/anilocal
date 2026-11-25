package com.anilocal.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import com.anilocal.app.ui.common.ContinueWatchingBar
import com.anilocal.app.ui.details.DetailsScreen
import com.anilocal.app.ui.downloads.DownloadsScreen
import com.anilocal.app.ui.explore.ExploreScreen
import com.anilocal.app.ui.extensions.ExtensionsScreen
import com.anilocal.app.ui.home.HomeScreen
import com.anilocal.app.ui.library.LibraryScreen
import com.anilocal.app.ui.more.MoreScreen
import com.anilocal.app.ui.navigation.AppNavigation
import com.anilocal.app.ui.navigation.NavDisplay
import com.anilocal.app.ui.navigation.Route
import com.anilocal.app.ui.navigation.TopTab
import com.anilocal.app.ui.player.PlayerScreen
import com.anilocal.app.ui.source.SourcePreferencesScreen
import com.anilocal.app.ui.theme.AniLocalTheme

class MainActivity : ComponentActivity() {
    /**
     * Set by the screen on display to see hardware keys before focus routing does (the player's
     * Space bar = play/pause, which has to win over whatever button keyboard navigation focused).
     * Returns true to consume the event.
     */
    var keyInterceptor: ((KeyEvent) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AniLocalTheme { AppRoot() } }
    }

    // androidx.core's ComponentActivity tags its override of this framework method library-internal;
    // overriding and calling Activity.dispatchKeyEvent is ordinary, so lint's RestrictedApi is wrong.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        keyInterceptor?.invoke(event) == true || super.dispatchKeyEvent(event)
}

@Composable
private fun AppRoot() {
    val appVm: AppViewModel = appViewModel { AppViewModel(it.mal, it.progress) }
    LaunchedEffect(Unit) { appVm.onAppOpen() }   // throttled MAL sync on app open
    val resumeItem by appVm.resumeBar.collectAsStateWithLifecycle()
    val nav: AppNavigation = appViewModel { AppNavigation(createSavedStateHandle()) }
    val navigator = nav.navigator
    val current = navigator.current
    // The bar shows on a tab's root screen only; the player is fullscreen/immersive, so it bleeds
    // under where the system bars were and gets no Scaffold content padding.
    val showBottomBar = navigator.atTabRoot
    val isPlayer = current.route is Route.Player

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
                                navigator.navigate(Route.Player(item.anime.id, item.episodeNumber, item.positionMs))
                            },
                            onDismiss = appVm::dismissBar,
                        )
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                        TopTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = navigator.tab == tab,
                                onClick = { navigator.selectTab(tab) },
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
        NavDisplay(nav, modifier = if (isPlayer) Modifier else Modifier.padding(padding)) { entry ->
            val openDetail: (String) -> Unit = { navigator.navigate(Route.Detail(it)) }
            val play: (String, Int) -> Unit = { animeId, ep -> navigator.navigate(Route.Player(animeId, ep)) }
            when (val route = entry.route) {
                Route.Home -> HomeScreen(
                    onOpen = openDetail,
                    onResume = { id, ep, pos -> navigator.navigate(Route.Player(id, ep, pos)) },
                    onExplore = { navigator.selectTab(TopTab.Explore) },
                )
                Route.Explore -> ExploreScreen(onOpen = openDetail)
                Route.Library -> LibraryScreen(onOpen = openDetail)
                Route.Downloads -> DownloadsScreen(onPlay = play)
                Route.Settings -> MoreScreen(
                    onBrowseExtensions = { navigator.navigate(Route.Extensions) },
                    onConfigureSource = { navigator.navigate(Route.SourcePreferences(it)) },
                )
                Route.Extensions -> ExtensionsScreen(onBack = { navigator.back() })
                is Route.SourcePreferences -> SourcePreferencesScreen(route.sourceId, onBack = { navigator.back() })
                is Route.Detail -> DetailsScreen(
                    animeId = route.animeId,
                    onPlay = play,
                    onOpenDetail = openDetail,
                    onBack = { navigator.back() },
                )
                is Route.Player -> PlayerScreen(
                    route = route,
                    onBack = { navigator.back() },
                    // Kept on the route so a restored stack reopens the episode that was playing.
                    onEpisodeChanged = { navigator.update(entry.id, route.copy(episode = it, startMs = 0L)) },
                )
            }
        }
    }
}
