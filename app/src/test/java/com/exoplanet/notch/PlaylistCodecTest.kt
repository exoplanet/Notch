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

    @Test
    fun decodeDropsBrokenEntriesAndClampsResume() {
        val raw = """
            {
              "playlists": [
                {"id": " ", "name": "Nope"},
                {
                  "id": "ok",
                  "name": "  Kept  ",
                  "entries": [
                    {"type": "nope"},
                    {"type": "file", "id": "", "uri": "content://x"},
                    {"type": "file", "id": "blank", "uri": "  "},
                    {"type": "file", "id": "f", "uri": "content://song", "track": 0, "title": "  Hi  "},
                    {"type": "album", "id": "a", "treeUri": ""},
                    {"type": "album", "id": "album", "treeUri": "content://tree", "title": " ", "tracks": [
                      {"uri": " "},
                      {"uri": "content://t", "fileName": "", "disc": 0, "track": -2}
                    ]}
                  ]
                }
              ],
              "resume": {"playlistId": "ok", "mediaIndex": -4, "positionMs": -8}
            }
        """.trimIndent()
        val decoded = PlaylistCodec.decode(raw)
        assertEquals(listOf("ok"), decoded.playlists.map { it.id })
        assertEquals("Kept", decoded.playlists.single().name)
        val file = decoded.playlists.single().entries[0] as PlaylistEntry.SingleFile
        assertEquals("Hi", file.metadata.title)
        assertEquals(null, file.metadata.trackNumber)
        val album = decoded.playlists.single().entries[1] as PlaylistEntry.Album
        assertEquals("Album", album.title)
        assertEquals(1, album.tracks.size)
        assertEquals("audio", album.tracks.single().fileName)
        assertEquals(null, album.tracks.single().discTag)
        assertEquals(null, album.tracks.single().trackNumber)
        assertEquals(ResumePoint("ok", 0, 0L), decoded.resume)
    }
}