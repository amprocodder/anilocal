package com.anilocal.app.ui.player

import com.anilocal.app.domain.model.SkipMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackMarkersTest {
    @Test fun fullEpisodeTimingsCannotEndAShorterDownloadedClip() {
        val intro = SkipMarker(SkipMarker.Type.INTRO, 0, 90_000)
        val outro = SkipMarker(SkipMarker.Type.OUTRO, 1_200_000, 1_290_000)
        assertTrue(playableMarkers(listOf(intro, outro), 33_000).isEmpty())
    }

    @Test fun validWindowsRemainAvailableIncludingAnOutroEndingAtDuration() {
        val intro = SkipMarker(SkipMarker.Type.INTRO, 3_000, 15_000)
        val outro = SkipMarker(SkipMarker.Type.OUTRO, 25_000, 33_000)
        val invalid = SkipMarker(SkipMarker.Type.INTRO, -1, 1_000)
        assertEquals(listOf(intro, outro), playableMarkers(listOf(intro, outro, invalid), 33_000))
    }
}
