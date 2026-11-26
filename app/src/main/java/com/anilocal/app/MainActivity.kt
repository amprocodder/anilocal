package com.anilocal.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import com.anilocal.app.domain.model.ContinueWatching
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
import com.anilocal.app.ui.kit.NavigationBar
import com.anilocal.app.ui.kit.NavigationBarItem
import com.anilocal.app.ui.kit.Surface
import com.anilocal.app.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    /**
     * Set by the screen on display to see hardware keys before focus routing does (the player's
     * Space bar = play/pause, which has to win over whatever button keyboard navigation focused;
     * Home's F = resume). Returns true to consume the event. Whoever sets it clears it on leaving,
     * only if it's still theirs, so screens handing over in one frame can't clear each other's.
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
    val appVm: AppViewModel = appViewModel { AppViewModel(it.mal, it.progress, it.catalog, it.streams, it.downloads) }
    LaunchedEffect(Unit) { appVm.onAppOpen() }   // throttled MAL sync on app open
    val resumeItem by appVm.resumeBar.collectAsStateWithLifecycle()
    val nav: AppNavigation = appViewModel { AppNavigation(createSavedStateHandle()) }
    val navigator = nav.navigator
    val current = navigator.current
    // The bar shows on a tab's root screen only; the player is fullscreen/immersive, so it bleeds
    // under where the system bars were and gets no inset padding.
    val showBottomBar = navigator.atTabRoot
    val isPlayer = current.route is Route.Player
    val resumeFromBar: (ContinueWatching) -> Unit = { item ->
        navigator.navigate(Route.Player(item.anime.id, item.episodeNumber, item.positionMs))
    }

    // A hardware keyboard's F on Home resumes the resume bar's episode, as tapping the bar does
    // (the player opens fullscreen). Home has no text field for the key to be typed into; with no
    // bar showing, F passes through. The player installs its own keys once it's up.
    val host = LocalActivity.current as? MainActivity
    val homeResume = resumeItem.takeIf { current.route == Route.Home }
    DisposableEffect(host, homeResume) {
        val item = homeResume
        if (host == null || item == null) return@DisposableEffect onDispose {}
        val onKey: (KeyEvent) -> Boolean = onKey@{ event ->
            if (event.keyCode != KeyEvent.KEYCODE_F || !event.hasNoModifiers()) return@onKey false
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) resumeFromBar(item)
            true
        }
        host.keyInterceptor = onKey
        onDispose { if (host.keyInterceptor === onKey) host.keyInterceptor = null }
    }

    // Vertical insets are handled per-screen so edge-to-edge art (Home hero, Details header) can
    // draw under the status bar while other screens use statusBarsPadding() (or their own top
    // bar). Horizontal bars/cutouts (landscape 3-button nav, corner cutouts) stay padded here
    // for every route — no screen wants content under those.
    // Surface: the screen's background and default content colour (what Material's Scaffold gave).
    Surface(Modifier.fillMaxSize(), color = AppTheme.colors.background) {
        Column(Modifier.fillMaxSize()) {
            val sideInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
            Box(Modifier.weight(1f).then(if (isPlayer) Modifier else Modifier.windowInsetsPadding(sideInsets))) {
                NavDisplay(nav, modifier = Modifier.fillMaxSize()) { entry ->
                    val openDetail: (String) -> Unit = { navigator.navigate(Route.Detail(it)) }
                    val play: (String, Int) -> Unit = { animeId, ep -> navigator.navigate(Route.Player(animeId, ep)) }
                    val resume: (String, Int, Long) -> Unit = { id, ep, pos -> navigator.navigate(Route.Player(id, ep, pos)) }
                    when (val route = entry.route) {
                        Route.Home -> HomeScreen(
                            onOpen = openDetail,
                            onResume = resume,
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
                            onResume = resume,
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
            if (showBottomBar) {
                resumeItem?.let { item ->
                    ContinueWatchingBar(
                        item = item,
                        onResume = { resumeFromBar(item) },
                        onDismiss = appVm::dismissBar,
                    )
                }
                NavigationBar {
                    TopTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = navigator.tab == tab,
                            onClick = { navigator.selectTab(tab) },
                            icon = tab.icon,
                            label = tab.label,
                        )
                    }
                }
            }
        }
    }
}
