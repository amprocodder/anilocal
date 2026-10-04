package com.anilocal.app.ui.player

import com.anilocal.app.domain.model.SkipMarker

/**
 * Keep cached AniSkip timings inside the media window. Marker data can belong to a different cut
 * of an episode (or to a longer online copy than a downloaded clip), so seeking to an unchecked
 * end time would jump beyond the playable content and can prevent normal episode completion.
 */
internal fun playableMarkers(markers: List<SkipMarker>, durationMs: Long): List<SkipMarker> =
    if (durationMs <= 0L) emptyList()
    else markers.filter { marker ->
        marker.startMs >= 0L && marker.endMs > marker.startMs && marker.endMs <= durationMs
    }
