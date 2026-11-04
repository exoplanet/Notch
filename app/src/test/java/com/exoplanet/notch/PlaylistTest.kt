package com.exoplanet.notch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistTest {
    @Test
    fun positionLabelIsOneBasedIndexOverCount() {
        assertEquals("1 / 3", Playlist.positionLabel(0, 3))
        assertEquals("2 / 3", Playlist.positionLabel(1, 3))
        assertEquals("3 / 3", Playlist.positionLabel(2, 3))
    }

    @Test
    fun positionLabelIsHiddenWhenQueueIsEmpty() {
        assertNull(Playlist.positionLabel(0, 0))
        assertNull(Playlist.positionLabel(-1, 0))
    }

    @Test
    fun trackTitlePrefersMetadataThenPath() {
        assertEquals("song.flac", Playlist.trackTitle("song.flac", "ignored"))
        assertEquals("clip.wav", Playlist.trackTitle(null, "clip.wav"))
        assertEquals("Audio", Playlist.trackTitle(null, null))
        assertEquals("Audio", Playlist.trackTitle("  ", ""))
    }
}
