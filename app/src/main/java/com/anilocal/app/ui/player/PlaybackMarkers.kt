package com.anilocal.app.ui.player

import com.anilocal.app.domain.model.SkipMarker

/**
 * Keep skip windows inside the media window. Marker data can belong to a different cut of an
 * episode (or to a longer online copy than a downloaded clip), so seeking to an unchecked end time
 * would jump beyond the playable content and can prevent normal episode completion.
 *
 * An OUTRO that starts inside the file but runs past its end is clamped, not dropped: the ending
 * really is there — this cut is merely shorter after it (no next-episode preview, or a window
 * measured on a longer copy). Skipping it lands exactly on the end, which still yields a clean
 * STATE_ENDED → auto-advance. Dropping it was a common way to lose the "Skip Outro" button.
 */
internal fun playableMarkers(markers: List<SkipMarker>, durationMs: Long): List<SkipMarker> =
    if (durationMs <= 0L) emptyList()
    else markers.mapNotNull { marker ->
        val clamped =
            if (marker.type == SkipMarker.Type.OUTRO && marker.endMs > durationMs) marker.copy(endMs = durationMs)
            else marker
        clamped.takeIf { it.startMs >= 0L && it.endMs > it.startMs && it.endMs <= durationMs }
    }

/**
 * An OUTRO that runs to (or within [tailMs] of) the end of a [durationMs]-long file: skipping it
 * finishes the episode rather than landing anywhere worth watching.
 */
internal fun SkipMarker.endsEpisode(durationMs: Long, tailMs: Long): Boolean =
    type == SkipMarker.Type.OUTRO && durationMs > 0L && endMs >= durationMs - tailMs
