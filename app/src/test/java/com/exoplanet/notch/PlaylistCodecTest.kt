package com.exoplanet.notch

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class PlaylistCodecTest {

    @Test
    fun roundTripWithEmptyEntries() {
        val id = UUID.randomUUID().toString()
        val playlist = SavedPlaylist(
            id = id,
            name = "Empty Playlist",
            createdAt = 1000L,
            updatedAt = 1000L,
            entries = emptyList(),
        )
        val library = SavedLibrary(playlists = listOf(playlist), resume = null)

        val encoded = PlaylistCodec.encode(library)
        val decoded = PlaylistCodec.decode(encoded)

        assertEquals(library, decoded)
        assertEquals(0, decoded.playlists.first().entries.size)
    }

    @Test
    fun roundTripWithMixedEntries() {
        val file = PlaylistEntry.SingleFile(
            "file-1",
            "content://song",
            TrackTags(title = "Song", artist = "Ada"),
        )
        val album = PlaylistEntry.Album(
            id = "album-1",
            treeUri = "content://tree",
            title = "Night",
            tracks = listOf(
                AlbumTrack(
                    uri = "content://t1",
                    fileName = "01.flac",
                    relativeFolder = "CD1",
                    discTag = 1,
                    trackNumber = 1,
                    title = "Intro",
                    artist = "Ada",
                    album = "Night",
                ),
            ),
        )
        val playlist = SavedPlaylist(
            id = UUID.randomUUID().toString(),
            name = "Mixed",
            createdAt = 1000L,
            updatedAt = 1000L,
            entries = listOf(file, album),
        )
        val library = SavedLibrary(playlists = listOf(playlist), resume = null)

        val encoded = PlaylistCodec.encode(library)
        val decoded = PlaylistCodec.decode(encoded)

        assertEquals(library, decoded)
        assertEquals(2, decoded.playlists.first().entries.size)
    }
}