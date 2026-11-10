package com.exoplanet.notch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumScannerTest {
    @Test
    fun audioMimeIsAcceptedAndVideoMimeIsRejected() {
        assertTrue(AlbumScanner.isAudio("song.mp4", "audio/mp4"))
        assertTrue(AlbumScanner.isAudio(null, "audio/mpeg"))
        assertFalse(AlbumScanner.isAudio("song.mp4", "video/mp4"))
        assertFalse(AlbumScanner.isAudio("song.m4a", "video/mp4"))
    }

    @Test
    fun extensionIsUsedOnlyWhenTheMimeIsNotAudioOrVideo() {
        assertTrue(AlbumScanner.isAudio("Song.MP3", null))
        assertTrue(AlbumScanner.isAudio("book.m4b", "application/octet-stream"))
        assertTrue(AlbumScanner.isAudio("track.FLAC", null))
        assertFalse(AlbumScanner.isAudio("clip.mp4", null))
        assertFalse(AlbumScanner.isAudio("notes.mp3.bak", null))
        assertFalse(AlbumScanner.isAudio(null, null))
        assertFalse(AlbumScanner.isAudio("folder", "inode/directory"))
    }
}
