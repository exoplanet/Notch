package com.exoplanet.notch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistStoreTest {
    @Test
    fun jsonRoundTripKeepsEntriesAndResume() {
        val library = sampleLibrary()
        val flagged = library.copy(
            playlists = listOf(
                library.playlists.single().copy(
                    entries = listOf(
                        (library.playlists.single().entries[0] as PlaylistEntry.SingleFile).copy(unavailable = true),
                        library.playlists.single().entries[1],
                    ),
                ),
            ),
        )
        val decoded = PlaylistCodec.decode(PlaylistCodec.encode(flagged))
        assertEquals(library, decoded)
        val file = decoded.playlists.single().entries[0] as PlaylistEntry.SingleFile
        assertFalse(file.unavailable)
    }

    @Test
    fun corruptJsonDecodesToAnEmptyLibrary() {
        assertEquals(SavedLibrary(), PlaylistCodec.decode(null))
        assertEquals(SavedLibrary(), PlaylistCodec.decode("not json"))
    }

    @Test
    fun loadSkipsAMissingTrackAndKeepsItFlagged() {
        val entries = listOf(
            PlaylistEntry.SingleFile("gone", "content://gone", TrackTags(title = "Gone")),
            PlaylistEntry.SingleFile("ok", "content://ok", TrackTags(title = "Ok")),
        )
        val loaded = PlaylistRefresh.refresh(entries, scan = { AlbumScanResult.Failed }) { it == "content://ok" }
        val queue = Playlist(loaded)
        assertEquals(listOf("Ok"), queue.flatten().map { it.title })
        assertTrue((loaded[0] as PlaylistEntry.SingleFile).unavailable)
        assertEquals(0, queue.mediaStart(1))
        assertEquals(0, queue.entries[0].mediaCount)
        assertEquals(1, queue.entries[1].mediaCount)
    }

    @Test
    fun failedAlbumScanKeepsTheCacheAndFlagsAnUnreadableTrack() {
        val album = PlaylistEntry.Album(
            id = "album",
            treeUri = "content://tree",
            title = "Night",
            tracks = listOf(
                track("content://t1", "One", disc = 1, number = 1),
                track("content://t2", "Two", disc = 1, number = 2),
            ),
        )
        val loaded = PlaylistRefresh.refresh(listOf(album), scan = { AlbumScanResult.Failed }) {
            it != "content://t2"
        }
        val result = loaded.single() as PlaylistEntry.Album
        assertEquals(listOf("content://t1", "content://t2"), result.tracks.map { it.uri })
        assertFalse(result.tracks[0].unavailable)
        assertTrue(result.tracks[1].unavailable)
        assertEquals(listOf("One"), Playlist(loaded).flatten().map { it.title })
    }

    @Test
    fun successfulScanReplacesTheCacheInDiscOrder() {
        val album = PlaylistEntry.Album(
            id = "album",
            treeUri = "content://tree",
            title = "Night",
            tracks = listOf(track("content://old-cache", "Cached", disc = 1, number = 1)),
        )
        val scanned = listOf(
            track("content://new", "New", disc = 1, number = 2),
            track("content://kept", "Kept", disc = 1, number = 1),
        )
        val loaded = PlaylistRefresh.refresh(listOf(album), scan = { AlbumScanResult.Ok(scanned) }) { true }
        val result = loaded.single() as PlaylistEntry.Album
        assertEquals(listOf("Kept", "New"), result.tracks.map { it.title })
        assertEquals(listOf("Kept", "New"), Playlist(loaded).flatten().map { it.title })
    }

    @Test
    fun renameAndDeleteUpdateTheLibrary() {
        val first = saved("a", "Morning", created = 5, updated = 5, "content://a")
        val second = saved("b", "Evening", created = 6, updated = 6, "content://b")
        val library = SavedLibrary(listOf(first, second), ResumePoint("a", 0, 10))
        val renamed = library.rename("a", "  Dawn  ", now = 40)
        assertEquals("Dawn", renamed?.playlists?.first()?.name)
        assertEquals(5L, renamed?.playlists?.first()?.createdAt)
        assertEquals(40L, renamed?.playlists?.first()?.updatedAt)
        assertEquals("Evening", renamed?.playlists?.get(1)?.name)
        assertNull(library.rename("a", "   ", now = 50))
        assertNull(library.rename("missing", "Other", now = 50))

        val deleted = renamed!!.delete("a")
        assertEquals(listOf("b"), deleted?.library?.playlists?.map { it.id })
        assertNull(deleted?.library?.resume)
        assertNull(library.delete("missing"))
    }

    @Test
    fun deleteReleasesOnlyUrisNoOtherPlaylistUses() {
        val sharedFile = PlaylistEntry.SingleFile("file", "content://shared", TrackTags(title = "Shared"))
        val onlyHere = PlaylistEntry.SingleFile("only", "content://only-file", TrackTags(title = "Only"))
        val album = PlaylistEntry.Album(
            id = "album",
            treeUri = "content://tree",
            title = "Night",
            tracks = listOf(
                track("content://track", "Track", disc = 1, number = 1),
                track("content://shared", "Shared again", disc = 1, number = 2),
            ),
        )
        val otherAlbum = PlaylistEntry.Album(
            id = "other-album",
            treeUri = "content://tree",
            title = "Night",
            tracks = listOf(track("content://track", "Track", disc = 1, number = 1)),
        )
        val first = SavedPlaylist("a", "A", 1, 1, listOf(sharedFile, onlyHere, album))
        val second = SavedPlaylist("b", "B", 2, 2, listOf(sharedFile, otherAlbum))
        val deleted = SavedLibrary(listOf(first, second)).delete("a")
        assertEquals(setOf("content://only-file"), deleted?.releasedUris)
        assertEquals(listOf("b"), deleted?.library?.playlists?.map { it.id })

        val last = SavedLibrary(listOf(second)).delete("b")
        assertEquals(setOf("content://shared", "content://tree", "content://track"), last?.releasedUris)
    }

    private fun sampleLibrary(): SavedLibrary {
        val file = PlaylistEntry.SingleFile(
            id = "file-1",
            uri = "content://song",
            metadata = TrackTags(title = "Song", artist = "Ada", album = "Singles", trackNumber = 3, discNumber = 1),
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
        return SavedLibrary(
            playlists = listOf(SavedPlaylist("pl-1", "Evening \"set\"", 10, 20, listOf(file, album))),
            resume = ResumePoint("pl-1", 1, 1500),
        )
    }

    private fun saved(id: String, name: String, created: Long, updated: Long, uri: String): SavedPlaylist {
        return SavedPlaylist(
            id = id,
            name = name,
            createdAt = created,
            updatedAt = updated,
            entries = listOf(PlaylistEntry.SingleFile(id, uri, TrackTags(title = name))),
        )
    }

    private fun track(uri: String, title: String, disc: Int, number: Int): AlbumTrack {
        return AlbumTrack(
            uri = uri,
            fileName = "$number.flac",
            relativeFolder = "CD$disc",
            discTag = disc,
            trackNumber = number,
            title = title,
        )
    }
}
