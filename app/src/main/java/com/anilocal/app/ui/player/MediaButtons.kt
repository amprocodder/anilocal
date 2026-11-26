package com.anilocal.app.ui.player

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.view.KeyEvent
import androidx.core.content.IntentCompat

/**
 * The system media session, so Bluetooth headset/earbud buttons (and a car, a watch, the Assistant)
 * can play and pause — nothing else. Built on the platform's own [MediaSession]: the player state it
 * advertises comes from the view model ([setState]), and both commands go through the view model
 * ([setPlaying]) so a press follows the overlay button's rules — the play intent outlives a player
 * swap, and an ended episode replays from the start. Seeking, skipping, stop and speed aren't
 * offered (only PLAY/PAUSE actions are advertised), so nothing can drive the ExoPlayer behind the
 * view model's back — e.g. "next" on a single item would jump to the end and auto-advance.
 *
 * A headset's single play/pause button ([KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE] / HEADSETHOOK) toggles
 * the play INTENT ([togglePlay]) rather than going by the player's state: a player that's still
 * connecting or reconnecting isn't playing yet, so a state-based toggle could never pause it. Acting on
 * the first ACTION_DOWN (a held button repeats) also skips the platform's double-tap wait (double tap
 * = "next", which isn't offered). Dedicated play and pause keys map to [setPlaying].
 */
internal class PlayerMediaSession(
    context: Context,
    private val setPlaying: (Boolean) -> Unit,
    private val togglePlay: () -> Unit,
) {
    private val session = MediaSession(context, "AniLocalPlayer")

    init {
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = setPlaying(true)

            override fun onPause() = setPlaying(false)

            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                val key = IntentCompat.getParcelableExtra(mediaButtonIntent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                if (key == null || (key.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && key.keyCode != KeyEvent.KEYCODE_HEADSETHOOK)) {
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }
                if (key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0) togglePlay()
                return true
            }
        })
        setState(playing = false, buffering = false)
        session.isActive = true
    }

    /** Off while the player is hidden (closed, or warmed up off screen): no button may start it unseen. */
    var active: Boolean
        get() = session.isActive
        set(value) { session.isActive = value }

    /** What the session advertises: playing, waiting to play (connecting/buffering), or paused. */
    fun setState(playing: Boolean, buffering: Boolean) {
        val state = when {
            playing -> PlaybackState.STATE_PLAYING
            buffering -> PlaybackState.STATE_BUFFERING
            else -> PlaybackState.STATE_PAUSED
        }
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (playing) 1f else 0f)
                .build(),
        )
    }

    /** The title a car/watch/lock-screen control shows. */
    fun setTitle(title: String, episode: Int) {
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Episode $episode")
                .build(),
        )
    }

    fun release() {
        session.isActive = false
        session.release()
    }
}
