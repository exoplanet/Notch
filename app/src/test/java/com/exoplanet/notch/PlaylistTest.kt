package com.exoplanet.notch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test
    fun appendingTheSamePlaylistTwiceKeepsIndependentCopies() {
        val file = PlaylistEntry.SingleFile("file1", "content://a", TrackTags(title = "A"))
        val album = PlaylistEntry.Album(
            id = "album1",
            treeUri = "content://tree",
            title = "Album",
            tracks = listOf(AlbumTrack("content://t", "1.m4a", title = "One")),
        )
        val source = listOf(file, album)
        val queue = Playlist()
            .appendAll(source.map { it.withNewId() })
            .appendAll(source.map { it.withNewId() })

        assertEquals(4, queue.entries.size)
        assertEquals(4, queue.entries.map { it.id }.distinct().size)
        source.forEach { original ->
            assertEquals(false, queue.entries.any { it.id == original.id })
        }
        val firstCopy = queue.entries[0] as PlaylistEntry.SingleFile
        assertNotEquals(file.id, firstCopy.id)
        assertEquals("A", firstCopy.metadata.title)
        val remaining = queue.removeEntry(firstCopy.id)
        assertEquals(3, remaining.entries.size)
        assertEquals(1, remaining.entries.count { it is PlaylistEntry.SingleFile && it.metadata.title == "A" })
        assertEquals(2, remaining.entries.count { it is PlaylistEntry.Album })
    }

    @Test
    fun removeEntryRemovesEntry() {
        val file = PlaylistEntry.SingleFile("file1", "content://file1", TrackTags(title = "File"))
        val album = PlaylistEntry.Album(
            id = "album1",
            treeUri = "content://album1",
            title = "Album",
            tracks = listOf(
                AlbumTrack("track1", "1.mp3", "folder", 1, 1, "Track 1", "Artist", "Album"),
                AlbumTrack("track2", "2.mp3", "folder", 1, 2, "Track 2", "Artist", "Album"),
            ),
        )
        val playlist = Playlist(listOf(file, album))

        val withoutFile = playlist.removeEntry("file1")
        assertEquals(1, withoutFile.entries.size)
        assertEquals("album1", withoutFile.entries[0].id)
    }

    @Test
    fun removeTrackRemovesTrackFromAlbum() {
        val track1 = AlbumTrack("track1", "1.mp3", "folder", 1, 1, "Track 1", "Artist", "Album")
        val track2 = AlbumTrack("track2", "2.mp3", "folder", 1, 2, "Track 2", "Artist", "Album")
        val album = PlaylistEntry.Album(
            id = "album1",
            treeUri = "content://album1",
            title = "Album",
            tracks = listOf(track1, track2),
        )
        val playlist = Playlist(listOf(album))

        val withoutFirstTrack = playlist.removeTrack("album1", 0)
        assertEquals(1, withoutFirstTrack.entries.size)
        val albumAfter = withoutFirstTrack.entries[0] as PlaylistEntry.Album
        assertEquals(1, albumAfter.tracks.size)
        assertEquals("track2", albumAfter.tracks[0].uri)
    }

    @Test
    fun flattenContainsAllAvailableTracks() {
        val file = PlaylistEntry.SingleFile("file1", "content://file1", TrackTags(title = "File"))
        val track1 = AlbumTrack("track1", "1.mp3", "folder", 1, 1, "Track 1", "Artist", "Album")
        val track2 = AlbumTrack("track2", "2.mp3", "folder", 1, 2, "Track 2", "Artist", "Album")
        val album = PlaylistEntry.Album(
            id = "album1",
            treeUri = "content://album1",
            title = "Album",
            tracks = listOf(track1, track2),
        )
        val playlist = Playlist(listOf(file, album))

        val flat = playlist.flatten()
        assertEquals(3, flat.size)
        assertEquals("File", flat[0].title)
        assertEquals("Track 1", flat[1].title)
        assertEquals("Track 2", flat[2].title)
    }

    @Test
    fun mediaStartAndRange() {
        val file = PlaylistEntry.SingleFile("file1", "content://file1", TrackTags(title = "File"))
        val track1 = AlbumTrack("track1", "1.mp3", "folder", 1, 1, "Track 1", "Artist", "Album")
        val track2 = AlbumTrack("track2", "2.mp3", "folder", 1, 2, "Track 2", "Artist", "Album")
        val album = PlaylistEntry.Album(
            id = "album1",
            treeUri = "content://album1",
            title = "Album",
            tracks = listOf(track1, track2),
        )
        val playlist = Playlist(listOf(file, album))

        assertEquals(0, playlist.mediaStart(0))
        assertEquals(0..0, playlist.mediaRange(0))
        assertEquals(1, playlist.mediaStart(1))
        assertEquals(1..2, playlist.mediaRange(1))
    }
}