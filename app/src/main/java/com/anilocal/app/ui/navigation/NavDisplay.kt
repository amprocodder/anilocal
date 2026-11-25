package com.anilocal.app.ui.navigation

import android.os.Bundle
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * Activity-scoped holder for the [Navigator] and the per-screen [ViewModelStore]s, so both survive
 * configuration changes; the stacks themselves also survive process death (saved as JSON in the
 * activity's saved state). Each back-stack entry gets its own store — a screen's ViewModels live
 * exactly as long as its entry is on some stack, and are cleared once it's popped and off screen.
 */
class AppNavigation(savedState: SavedStateHandle) : ViewModel() {
    val navigator = Navigator(
        savedState.get<Bundle>(STATE_KEY)?.getString(STATE_KEY)
            ?.let { runCatching { json.decodeFromString(NavState.serializer(), it) }.getOrNull() },
    )

    private val owners = HashMap<String, EntryOwner>()

    init {
        savedState.setSavedStateProvider(STATE_KEY) {
            Bundle().apply { putString(STATE_KEY, json.encodeToString(NavState.serializer(), navigator.state())) }
        }
    }

    internal fun ownerOf(id: String): ViewModelStoreOwner = owners.getOrPut(id) { EntryOwner() }

    /** Clears the ViewModels of every entry not in [keep]; [onRemoved] gets each disposed id. */
    internal fun prune(keep: Set<String>, onRemoved: (String) -> Unit) {
        (owners.keys - keep).forEach { id ->
            owners.remove(id)?.viewModelStore?.clear()
            onRemoved(id)
        }
    }

    override fun onCleared() {
        owners.values.forEach { it.viewModelStore.clear() }
        owners.clear()
    }

    private class EntryOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
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
 * and nothing at all into or out of the player (its SurfaceView can't fade with the Compose layers).
 * A predictive back gesture seeks the pop animation with the finger and commits or springs back.
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

    PredictiveBackHandler(enabled = navigator.canGoBack) { progress ->
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
        transitionSpec = { navTransition(initialState, targetState, if (gesture) NavMove.Pop else navigator.lastMove) },
    ) { entry ->
        saveable.SaveableStateProvider(entry.id) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides nav.ownerOf(entry.id)) {
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

private fun navTransition(from: NavEntry, to: NavEntry, move: NavMove): ContentTransform {
    if (from.route is Route.Player || to.route is Route.Player) {
        return ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
    }
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
