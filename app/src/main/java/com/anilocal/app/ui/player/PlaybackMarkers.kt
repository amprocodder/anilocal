package com.anilocal.app.ui.player

import com.anilocal.app.domain.model.SkipMarker

/** Cached timings can belong to a different cut; never seek beyond the loaded video. */
internal fun playableMarkers(markers: List<SkipMarker>, durationMs: Long): List<SkipMarker> =
    markers.filter { it.startMs >= 0 && it.endMs > it.startMs && it.endMs <= durationMs }
