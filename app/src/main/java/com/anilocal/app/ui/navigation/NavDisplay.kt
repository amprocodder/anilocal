package com.anilocal.app.ui.navigation

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anilocal.app.ui.theme.Motion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

/**
 * Activity-scoped holder for the [Navigator] and the per-screen [ViewModelStore]s, so both survive
 * configuration changes; the stacks themselves also survive process death (saved as JSON in the
 * activity's saved state). Each back-stack entry gets its own store — a screen's ViewModels live
 * exactly as long as its entry is on some stack, and are cleared once it's popped and off screen.
 *
 * The one exception is the player: a closed player's store is kept as the *warm* player, and so is
 * one built off screen for the resume bar's episode ([warmUp]). Opening that same episode adopts it
 * — the new entry gets that store, so its screen finds the same PlayerViewModel and the episode
 * continues instead of loading. One is kept at a time; opening another episode releases it.
 */
class AppNavigation(savedState: SavedStateHandle) : ViewModel() {
    val navigator = Navigator(
        savedState.get<Bundle>(STATE_KEY)?.getString(STATE_KEY)
            ?.let { runCatching { json.decodeFromString(NavState.serializer(), it) }.getOrNull() },
    )

    private val owners = HashMap<String, EntryOwner>()
    // Each owned entry's latest route (the player's episode changes in place): what a closed player is
    // kept under.
    private val routes = HashMap<String, Route>()
    private var warm: WarmPlayer? = null

    init {
        savedState.setSavedStateProvider(STATE_KEY) {
            Bundle().apply { putString(STATE_KEY, json.encodeToString(NavState.serializer(), navigator.state())) }
        }
    }

    internal fun ownerOf(entry: NavEntry): ViewModelStoreOwner {
        routes[entry.id] = entry.route
        owners[entry.id]?.let { return it }
        val adopted = (entry.route as? Route.Player)?.let(::adoptWarm)
        return (adopted ?: EntryOwner()).also { owners[entry.id] = it }
    }

    /** Clears the ViewModels of every entry not in [keep] (a player's are kept warm instead);
     *  [onRemoved] gets each disposed id. */
    internal fun prune(keep: Set<String>, onRemoved: (String) -> Unit) {
        (owners.keys - keep).forEach { id ->
            val owner = owners.remove(id) ?: return@forEach
            val route = routes.remove(id)
            if (route is Route.Player) keepWarm(WarmPlayer(route, owner, warmedUp = false))
            else owner.viewModelStore.clear()
            onRemoved(id)
        }
    }

    /**
     * Builds the player for [route] off screen with [create] (a [type] the player screen asks its
     * store for), unless one for that episode is already kept — a closed one, or a warm-up made less
     * than [maxAgeMs] ago (its link may have expired since; a closed one's was in use, and it holds
     * the user's track and speed choices).
     */
    fun <VM : ViewModel> warmUp(route: Route.Player, type: KClass<VM>, maxAgeMs: Long, create: () -> VM) {
        warm?.let { held ->
            if (held.matches(route) && !(held.warmedUp && held.ageMs() > maxAgeMs)) return
        }
        val owner = EntryOwner()
        ViewModelProvider.create(owner, viewModelFactory { addInitializer(type) { create() } })[type]
        keepWarm(WarmPlayer(route, owner, warmedUp = true))
    }

    /** Releases the warm player (the app left the foreground, so nothing should hold a decoder). */
    fun releaseWarm() {
        warm?.owner?.viewModelStore?.clear()
        warm = null
    }

    private fun keepWarm(player: WarmPlayer) {
        releaseWarm()
        warm = player
    }

    // The warm player's store if it holds [route]'s episode; any other is released, since only one
    // player should hold a decoder.
    private fun adoptWarm(route: Route.Player): EntryOwner? {
        val held = warm ?: return null
        warm = null
        if (held.matches(route)) return held.owner
        held.owner.viewModelStore.clear()
        return null
    }

    override fun onCleared() {
        owners.values.forEach { it.viewModelStore.clear() }
        owners.clear()
        releaseWarm()
    }

    private class EntryOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private class WarmPlayer(route: Route.Player, val owner: EntryOwner, val warmedUp: Boolean) {
        private val animeId = route.animeId
        private val episode = route.episode
        private val since = SystemClock.elapsedRealtime()

        fun matches(route: Route.Player) = route.animeId == animeId && route.episode == episode
        fun ageMs() = SystemClock.elapsedRealtime() - since
    }

    private companion object {
        const val STATE_KEY = "nav"
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Shows [AppNavigation.navigator]'s current screen. Each entry's composition gets its own
 * rememberSaveable state (kept while it's on a stack, so a tab's scroll position survives switching
 * tabs) and ViewModelStore. Changes animate: a quick fade on tab switches, fade + scale on push/pop,
 * and the player rising over the screen it opens from and sinking back down when it closes — a pure
 * slide, since its video SurfaceView moves with the Compose layers but can't fade or scale with them.
 * A predictive back gesture seeks the pop animation with the finger and commits or springs back; a
 * screen being dragged away to Home ([Navigator.revealed]) seeks Home in beneath it the same way.
 */
@Composable
fun NavDisplay(
    nav: AppNavigation,
    modifier: Modifier = Modifier,
    content: @Composable (NavEntry) -> Unit,
) {
    val navigator = nav.navigator
    val saveable = rememberSaveableStateHolder()
    val current = navigator.current
    val transitionState = remember { SeekableTransitionState(current) }
    val transition = rememberTransition(transitionState, label = "nav")
    var gesture by remember { mutableStateOf(false) }

    LaunchedEffect(current) {
        when {
            // Same screen, route updated in place (the player's episode): no animation.
            transitionState.currentState.id == current.id -> transitionState.snapTo(current)
            !gesture -> transitionState.animateTo(current)
        }
    }

    // Home showing beneath a screen being dragged away to it: the transition seeks toward Home with
    // the drag (once any transition into that screen has finished). Let go without leaving, and Home
    // is put away again; if the screen did leave, the effect above finishes the transition from here.
    val revealed = navigator.revealed
    LaunchedEffect(revealed) {
        if (revealed == null) {
            val stillHere = transitionState.currentState.id == navigator.current.id
            if (stillHere && transitionState.targetState != transitionState.currentState) {
                transitionState.snapTo(transitionState.currentState)
            }
            return@LaunchedEffect
        }
        snapshotFlow { transitionState.currentState == transitionState.targetState }.first { it }
        snapshotFlow { navigator.revealProgress }.collect { transitionState.seekTo(it, revealed) }
    }

    PredictiveBackHandler(enabled = navigator.canGoBack && revealed == null) { progress ->
        val target = navigator.previous ?: return@PredictiveBackHandler
        gesture = true
        try {
            progress.collect { event -> transitionState.seekTo(event.progress, target) }
            gesture = false
            navigator.back()   // commit — the effect above finishes the animation from here
        } catch (cancelled: CancellationException) {
            gesture = false
            transitionState.animateTo(transitionState.currentState)   // spring back
        }
    }

    transition.AnimatedContent(
        modifier = modifier,
        contentKey = { it.id },
        transitionSpec = {
            navTransition(if (gesture) NavMove.Pop else navigator.lastMove, revealing = navigator.revealed != null)
        },
    ) { entry ->
        saveable.SaveableStateProvider(entry.id) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides nav.ownerOf(entry)) {
                content(entry)
            }
        }
    }

    // Once nothing is animating, dispose what's left the stacks: its ViewModels and saved UI state.
    val live = navigator.liveIds()
    val settled = !transition.isRunning && transitionState.currentState == transitionState.targetState
    LaunchedEffect(live, settled) {
        if (settled) nav.prune(live + transitionState.currentState.id) { saveable.removeState(it) }
    }
}

private fun AnimatedContentTransitionScope<NavEntry>.navTransition(move: NavMove, revealing: Boolean): ContentTransform {
    // Home showing through beneath a screen dragged away to it (Navigator.revealHome): it settles
    // forward from slightly back into place as the drag goes on — seeked with the finger. The dragged
    // screen moves itself, so here it's only kept, on top, until the drag ends.
    if (revealing) return ContentTransform(
        scaleIn(tween(Motion.PLAYER_CLOSE_MS, easing = LinearEasing), initialScale = REVEAL_SCALE),
        ExitTransition.KeepUntilTransitionsFinished,
        targetContentZIndex = -1f,
        sizeTransform = null,
    )
    val from = initialState.route
    val to = targetState.route
    // The player slides up from the bottom edge over the screen it was opened from (which holds
    // still underneath until it's covered), and slides back down to reveal it on close.
    if (to is Route.Player && from !is Route.Player) return ContentTransform(
        slideInVertically(tween(Motion.PLAYER_OPEN_MS, easing = Motion.Standard)) { it },
        ExitTransition.KeepUntilTransitionsFinished,
        targetContentZIndex = 1f,
        sizeTransform = null,
    )
    if (from is Route.Player && to !is Route.Player) return ContentTransform(
        EnterTransition.None,
        slideOutVertically(tween(Motion.PLAYER_CLOSE_MS, easing = Motion.Accelerate)) { it },
        targetContentZIndex = -1f,
        sizeTransform = null,
    )
    return when (move) {
        NavMove.Tab -> ContentTransform(fadeIn(tween(160)), fadeOut(tween(110)), sizeTransform = null)
        NavMove.Push -> ContentTransform(
            fadeIn(tween(220, delayMillis = 40)) + slideInVertically(tween(260)) { it / 24 },
            fadeOut(tween(160)),
            sizeTransform = null,
        )
        // The screen being left stays on top (z above the revealed one) while it shrinks away.
        NavMove.Pop -> ContentTransform(
            fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.96f),
            fadeOut(tween(200)) + scaleOut(tween(260), targetScale = 0.92f),
            targetContentZIndex = -1f,
            sizeTransform = null,
        )
    }
}

/** Home's scale beneath a screen that's just begun to be dragged away to it. */
private const val REVEAL_SCALE = 0.94f
