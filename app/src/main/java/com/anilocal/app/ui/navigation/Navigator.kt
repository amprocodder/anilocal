package com.anilocal.app.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import kotlinx.serialization.Serializable

/** One screen on a back stack. [id] is its identity for state/ViewModel scoping and stays the same
 *  when its [route] is updated in place (e.g. the player advancing to the next episode). */
@Serializable
data class NavEntry(val id: String, val route: Route)

/** The whole navigation state, as saved across process death. */
@Serializable
data class NavState(val tab: TopTab, val stacks: Map<TopTab, List<NavEntry>>, val nextId: Long)

/** How the last change was made — picks the transition (see NavDisplay). */
enum class NavMove { Push, Pop, Tab }

/**
 * The app's navigation, written out: one back stack per bottom tab, each starting at its tab's root.
 * Switching tabs keeps every tab's stack as it was (and its screens' state and ViewModels — see
 * NavDisplay); Back pops the current tab's stack, and from another tab's root returns to Home;
 * re-selecting the current tab returns it to its root. All state is snapshot state, so the UI
 * recomposes on every change.
 */
@Stable
class Navigator(saved: NavState? = null) {
    private val stacks: Map<TopTab, SnapshotStateList<NavEntry>> = TopTab.entries.associateWith { tab ->
        (saved?.stacks?.get(tab)?.takeIf { it.isNotEmpty() && it.first().route == tab.root }
            ?: listOf(NavEntry("root-${tab.name}", tab.root))).toMutableStateList()
    }
    private var nextId = saved?.nextId ?: 0L

    /** The selected bottom tab. */
    var tab by mutableStateOf(saved?.tab ?: TopTab.Home)
        private set

    var lastMove by mutableStateOf(NavMove.Push)
        private set

    private val stack get() = stacks.getValue(tab)

    /** The screen on display. */
    val current: NavEntry get() = stack.last()

    /** True while the current tab shows its root screen (the bottom bar is visible there). */
    val atTabRoot: Boolean get() = stack.size == 1

    /** The screen a back press would reveal, or null when back leaves the app. */
    val previous: NavEntry?
        get() = when {
            stack.size > 1 -> stack[stack.lastIndex - 1]
            tab != TopTab.Home -> stacks.getValue(TopTab.Home).last()
            else -> null
        }

    val canGoBack: Boolean get() = previous != null

    fun navigate(route: Route) {
        lastMove = NavMove.Push
        stack.add(NavEntry("e${nextId++}", route))
    }

    /** Pops the current screen (or returns to Home from another tab's root). False when there's
     *  nothing to go back to. */
    fun back(): Boolean {
        val s = stack
        return when {
            s.size > 1 -> { lastMove = NavMove.Pop; s.removeAt(s.lastIndex); true }
            tab != TopTab.Home -> { lastMove = NavMove.Pop; tab = TopTab.Home; true }
            else -> false
        }
    }

    fun selectTab(target: TopTab) {
        if (target == tab) {
            val s = stack
            if (s.size > 1) { lastMove = NavMove.Pop; s.removeRange(1, s.size) }
        } else {
            lastMove = NavMove.Tab
            tab = target
        }
    }

    /**
     * Home's root while it shows beneath the current screen, which a gesture is dragging away to it
     * (the player's swipe down): NavDisplay composes it under that screen. Null otherwise.
     */
    var revealed: NavEntry? by mutableStateOf(null)
        private set
    private var revealedBy: () -> Float = { 0f }

    /** How far the screen over [revealed] has been dragged away: 0 = still in place … 1 = gone. */
    val revealProgress: Float get() = if (revealed == null) 0f else revealedBy().coerceIn(0f, 1f)

    /** Shows Home's root beneath the current screen, as far revealed as [progress] reads (snapshot state). */
    fun revealHome(progress: () -> Float) {
        revealedBy = progress
        revealed = stacks.getValue(TopTab.Home).first()
    }

    /** The drag let go without leaving: the screen covers Home again. */
    fun cancelReveal() {
        revealed = null
        revealedBy = { 0f }
    }

    /**
     * Leaves the current screen for Home's root — where a swipe down from the player lands: the
     * screen is popped off its stack, Home's stack goes back to its root, and the Home tab is selected.
     */
    fun goHome() {
        cancelReveal()
        lastMove = NavMove.Pop
        stack.let { if (it.size > 1) it.removeAt(it.lastIndex) }
        stacks.getValue(TopTab.Home).let { if (it.size > 1) it.removeRange(1, it.size) }
        tab = TopTab.Home
    }

    /** Replaces the route of entry [id], keeping its identity (so its screen isn't rebuilt). */
    fun update(id: String, route: Route) {
        for (s in stacks.values) {
            val i = s.indexOfFirst { it.id == id }
            if (i >= 0) { s[i] = s[i].copy(route = route); return }
        }
    }

    /** Ids of every entry on any stack — everything else can have its state disposed. */
    fun liveIds(): Set<String> = stacks.values.flatMapTo(HashSet()) { s -> s.map { it.id } }

    fun state() = NavState(tab, stacks.mapValues { it.value.toList() }, nextId)
}
