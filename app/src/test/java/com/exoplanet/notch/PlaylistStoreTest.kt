package com.exoplanet.notch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

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
    fun successfulScanKeepsAMissingTrackFlaggedInDiscOrder() {
        val album = PlaylistEntry.Album(
            id = "album",
            treeUri = "content://tree",
            title = "Night",
            tracks = listOf(
                track("content://kept", "Kept", disc = 1, number = 1),
                track("content://gone", "Gone", disc = 1, number = 2),
                track("content://old-last", "Old last", disc = 2, number = 1),
            ),
        )
        val scanned = listOf(
            track("content://new", "New", disc = 1, number = 3),
            track("content://kept", "Kept", disc = 1, number = 1),
        )
        val loaded = PlaylistRefresh.refresh(listOf(album), scan = { AlbumScanResult.Ok(scanned) }) { true }
        val result = loaded.single() as PlaylistEntry.Album
        assertEquals(listOf("Kept", "Gone", "New", "Old last"), result.tracks.map { it.title })
        assertEquals(listOf(false, true, false, true), result.tracks.map { it.unavailable })
        assertEquals(listOf("Kept", "New"), Playlist(loaded).flatten().map { it.title })
    }

    @Test
    fun emptySuccessfulScanFlagsEveryCachedTrack() {
        val album = PlaylistEntry.Album(
            id = "album",
            treeUri = "content://tree",
            title = "Night",
            tracks = listOf(
                track("content://t1", "One", disc = 1, number = 1),
                track("content://t2", "Two", disc = 1, number = 2),
            ),
        )
        val loaded = PlaylistRefresh.refresh(listOf(album), scan = { AlbumScanResult.Ok(emptyList()) }) { true }
        val result = loaded.single() as PlaylistEntry.Album
        assertEquals(listOf("content://t1", "content://t2"), result.tracks.map { it.uri })
        assertTrue(result.tracks.all { it.unavailable })
        assertTrue(Playlist(loaded).flatten().isEmpty())
    }

    @Test
    fun failedOrBlankScanKeepsTheCacheAndAThrowingReadFlagsTheTrack() {
        val album = PlaylistEntry.Album(
            id = "album",
            treeUri = "content://tree",
            title = "Night",
            tracks = listOf(
                track("content://t1", "One", disc = 1, number = 1),
                track("", "Blank", disc = 1, number = 2),
            ),
        )
        val thrown = PlaylistRefresh.refresh(listOf(album), scan = { error("unreadable") }) { true }
        val afterThrow = thrown.single() as PlaylistEntry.Album
        assertEquals(listOf("content://t1", ""), afterThrow.tracks.map { it.uri })
        assertFalse(afterThrow.tracks[0].unavailable)
        assertTrue(afterThrow.tracks[1].unavailable)

        val blankTree = album.copy(treeUri = " ")
        val kept = PlaylistRefresh.refresh(listOf(blankTree), scan = { error("should not scan") }) { true }
        assertEquals(listOf("content://t1", ""), (kept.single() as PlaylistEntry.Album).tracks.map { it.uri })

        val scanned = PlaylistRefresh.refresh(listOf(album), scan = { AlbumScanResult.Ok(emptyList()) }) {
            throw IllegalStateException("grant")
        }
        assertTrue((scanned.single() as PlaylistEntry.Album).tracks.all { it.unavailable })
    }

    @Test
    fun rememberIgnoresAnUnknownPlaylist() {
        val library = SavedLibrary(playlists = listOf(SavedPlaylist("pl1", "Test", 1L, 2L, emptyList())))
        assertSame(library, library.remember(ResumePoint("missing", 0, 0L)))
        assertNull(library.commitPlaylist(" ", "Name", emptyList(), 3L))
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


    @Test
    fun saveNewAllowsEmptyEntries() {
        val library = SavedLibrary()
        val id = UUID.randomUUID().toString()
        val updated = library.saveNew(id, "New Playlist", emptyList(), 12345L)!!
        assertEquals(1, updated.playlists.size)
        assertEquals("New Playlist", updated.playlists[0].name)
        assertTrue(updated.playlists[0].entries.isEmpty())
    }

    @Test
    fun saveNewRejectsBlankName() {
        val library = SavedLibrary()
        assertNull(library.saveNew("", "Name", emptyList(), 12345L))
        assertNull(library.saveNew("id", "   ", emptyList(), 12345L))
    }

    @Test
    fun updateEntriesReturnsSameLibraryWhenUnchanged() {
        val playlist = SavedPlaylist("pl1", "Test", 1L, 2L, emptyList())
        val library = SavedLibrary(playlists = listOf(playlist))
        val updated = library.updateEntries(playlist.id, playlist.entries, 12345L)
        assertSame(library, updated)
    }

    @Test
    fun rememberAndClearResume() {
        val library = SavedLibrary(
            playlists = listOf(SavedPlaylist("pl1", "Test", 1L, 2L, emptyList())),
            resume = ResumePoint("pl1", 0, 100L)
        )
        val same = library.remember(ResumePoint("pl1", 0, 100L))
        assertSame(library, same)
        val changed = library.remember(ResumePoint("pl1", 5, 200L))
        assertNotSame(library, changed)
        assertEquals(ResumePoint("pl1", 5, 200L), changed.resume)
        val cleared = library.clearResume()
        assertNull(cleared.resume)
    }

    @Test
    fun releaseUrisDeduplicatesAndSkipsAlreadyUsed() {
        val sharedFile = PlaylistEntry.SingleFile("shared", "content://shared", TrackTags(title = "Shared"))
        val track1 = track("content://track1", "Track 1", disc = 1, number = 1)
        val album1 = PlaylistEntry.Album("album1", "content://tree1", "Album 1", tracks = listOf(track1))
        val track2 = track("content://track2", "Track 2", disc = 1, number = 1)
        val album2 = PlaylistEntry.Album("album2", "content://tree2", "Album 2", tracks = listOf(track2))
        val library = SavedLibrary(
            playlists = listOf(
                SavedPlaylist("p1", "Playlist 1", 1L, 2L, listOf(sharedFile, album1)),
                SavedPlaylist("p2", "Playlist 2", 3L, 4L, listOf(sharedFile, album2)),
            )
        )
        val deletion = library.delete("p1")
        assertEquals(setOf("content://tree1", "content://track1"), deletion?.releasedUris)
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

    @Test
    fun commitPlaylistStoresTracksAndReleasesARemovedUri() {
        val kept = PlaylistEntry.SingleFile("keep", "content://keep", TrackTags(title = "Keep"))
        val dropped = PlaylistEntry.SingleFile("drop", "content://drop", TrackTags(title = "Drop"))
        val created = SavedLibrary().commitPlaylist("mix", "Mix", listOf(kept, dropped), 10L)
        assertEquals(listOf("Mix"), created?.library?.playlists?.map { it.name })
        assertEquals(emptySet<String>(), created?.releasedUris)

        val renamed = created!!.library.commitPlaylist("mix", "Evening", listOf(kept), 11L)
        assertEquals("Evening", renamed?.library?.playlists?.single()?.name)
        assertEquals(setOf("content://drop"), renamed?.releasedUris)
        assertEquals(1, renamed?.library?.playlists?.single()?.entries?.size)
    }

    @Test
    fun commitPlaylistKeepsAUriAnotherPlaylistUses() {
        val shared = PlaylistEntry.SingleFile("shared", "content://shared", TrackTags(title = "Shared"))
        val only = PlaylistEntry.SingleFile("only", "content://only", TrackTags(title = "Only"))
        val library = SavedLibrary(
            playlists = listOf(
                SavedPlaylist("a", "A", 1L, 1L, listOf(shared, only)),
                SavedPlaylist("b", "B", 2L, 2L, listOf(shared)),
            ),
        )
        val written = library.commitPlaylist("a", "A", listOf(only), 3L)
        assertEquals(emptySet<String>(), written?.releasedUris)
        assertEquals(setOf("content://only"), PlaylistUris.collect(written!!.library.playlists.first()))
    }

    @Test
    fun commitPlaylistRejectsABlankName() {
        val library = SavedLibrary()
        assertNull(library.commitPlaylist("id", "   ", emptyList(), 1L))
        val existing = library.saveNew("id", "Named", emptyList(), 1L)!!
        assertNull(existing.commitPlaylist("id", " ", emptyList(), 2L))
    }

    @Test
    fun urisOnlyInDraftSkipsUrisAlreadySaved() {
        val saved = PlaylistEntry.SingleFile("saved", "content://saved", TrackTags(title = "Saved"))
        val fresh = PlaylistEntry.SingleFile("fresh", "content://fresh", TrackTags(title = "Fresh"))
        val library = SavedLibrary(playlists = listOf(SavedPlaylist("a", "A", 1L, 1L, listOf(saved))))
        assertEquals(setOf("content://fresh"), library.urisOnlyInDraft(listOf(saved, fresh)))
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
