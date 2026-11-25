package com.anilocal.app.ui.player

import android.content.Intent
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.core.content.IntentCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession

/**
 * The player as the system media session exposes it to Bluetooth headset/earbud buttons, a car, a
 * watch or the Assistant. They may play and pause, nothing else, and both go through the view
 * model ([setPlaying]) so a press follows the overlay button's rules: the play intent outlives a
 * player swap, and an ended episode replays from the start. Everything that would drive the
 * ExoPlayer behind the view model's back is withheld — prepare (Media3's play handler prepares an
 * IDLE player, i.e. one that's still connecting or has failed into recovery), stop, speed,
 * playlist edits, and seek-to-next, which on a single item jumps to the end and auto-advances.
 */
@OptIn(UnstableApi::class)
internal class SessionPlayer(
    player: Player,
    private val setPlaying: (Boolean) -> Unit,
) : ForwardingPlayer(player) {

    override fun play() = setPlaying(true)

    override fun pause() = setPlaying(false)

    override fun setPlayWhenReady(playWhenReady: Boolean) = setPlaying(playWhenReady)

    override fun isCommandAvailable(command: Int): Boolean =
        command in ALLOWED && super.isCommandAvailable(command)

    override fun getAvailableCommands(): Player.Commands {
        val available = super.getAvailableCommands()
        return Player.Commands.Builder()
            .apply { ALLOWED.forEach { addIf(it, available.contains(it)) } }
            .build()
    }

    private companion object {
        val ALLOWED = intArrayOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_AUDIO_ATTRIBUTES,
        )
    }
}

/**
 * A headset's single play/pause button toggles the view model's play intent, exactly like the
 * overlay's button. Left to Media3, the toggle is decided from the player's state, and a player
 * that's still connecting or reconnecting is IDLE — so every press would read as "play" and the
 * button could never pause it. Handling it here also skips Media3's double-tap wait (a double tap
 * means "next", which the session doesn't offer). Dedicated play and pause keys, and controllers'
 * play/pause commands, still reach [SessionPlayer].
 */
@OptIn(UnstableApi::class)
internal fun mediaButtonCallback(togglePlay: () -> Unit) = object : MediaSession.Callback {
    override fun onMediaButtonEvent(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        intent: Intent,
    ): Boolean {
        // Media3 only passes on ACTION_DOWN; a held button repeats it, so act on the first press.
        val key = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            ?: return false
        if (key.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && key.keyCode != KeyEvent.KEYCODE_HEADSETHOOK) {
            return false
        }
        if (key.repeatCount == 0) togglePlay()
        return true
    }
}
