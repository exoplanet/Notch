package com.exoplanet.notch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistModelTest {
    @Test
    fun flattenKeepsMixedEntriesInOrder() {
        val album = PlaylistEntry.Album(
            id = "album",
            treeUri = "tree",
            title = "Album",
            tracks = listOf(
                albumTrack("disc-two.flac", discTag = 2, trackNumber = 1),
                albumTrack("disc-one-b.flac", discTag = 1, trackNumber = 2),
                albumTrack("disc-one-a.flac", discTag = 1, trackNumber = 1),
            ),
        )
        val queue = Playlist(
            listOf(
                single("first", "picked-first"),
                single("second", "picked-second"),
            ),
        ).appendAlbum(album)
        assertEquals(
            listOf("picked-first", "picked-second", "disc-one-a.flac", "disc-one-b.flac", "disc-two.flac"),
            queue.flatten().map { it.title },
        )
        assertEquals(listOf("first", "second", "album", "album", "album"), queue.flatten().map { it.entryId })
    }

    @Test
    fun movingAnAlbumMovesTheWholeBlock() {
        val queue = Playlist(
            listOf(
                single("s1", "one"),
                PlaylistEntry.Album("album", "tree", "Album", listOf(albumTrack("a.flac"), albumTrack("b.flac"))),
                single("s2", "two"),
            ),
        )
        assertEquals(MediaMove(1, 3, 0), queue.mediaMove(1, 0))
        assertEquals(MediaMove(1, 3, 2), queue.mediaMove(1, 2))
        val moved = queue.moveEntry(1, 2)
        assertEquals(listOf("one", "two", "a.flac", "b.flac"), moved.flatten().map { it.title })
        assertEquals(listOf("s1", "s2", "album"), moved.entries.map { it.id })
    }

    @Test
    fun removingAnEntryDropsItsMediaRange() {
        val queue = Playlist(
            listOf(
                single("s1", "one"),
                PlaylistEntry.Album("album", "tree", "Album", listOf(albumTrack("a.flac"), albumTrack("b.flac"))),
                single("s2", "two"),
            ),
        )
        assertEquals(1 until 3, queue.mediaRange(1))
        val removed = queue.removeEntry("album")
        assertEquals(listOf("one", "two"), removed.flatten().map { it.title })
        assertNull(removed.locationAt(2))
        assertEquals("s2", removed.locationAt(1)?.entryId)
    }

    @Test
    fun mediaIndexMapsToTheEntryAndTrack() {
        val queue = Playlist(
            listOf(
                single("s1", "one"),
                PlaylistEntry.Album("album", "tree", "Album", listOf(albumTrack("a.flac"), albumTrack("b.flac"))),
            ),
        )
        val secondTrack = queue.locationAt(2)
        assertEquals("album", secondTrack?.entryId)
        assertEquals(1, secondTrack?.entryIndex)
        assertEquals(1, secondTrack?.trackIndex)
        assertEquals(2, secondTrack?.mediaIndex)
        assertEquals(
            listOf("s1#0", "album#0", "album#1"),
            queue.flatten().map { "${it.entryId}#${it.trackIndex}" },
        )
        val withoutTrack = queue.removeTrack("album", 0)
        assertEquals(listOf("one", "b.flac"), withoutTrack.flatten().map { it.title })
        assertEquals(0, withoutTrack.locationAt(1)?.trackIndex)
        val emptied = withoutTrack.removeTrack("album", 0)
        assertEquals(listOf("one"), emptied.flatten().map { it.title })
        assertEquals(listOf("s1"), emptied.entries.map { it.id })
    }

    private fun single(id: String, title: String): PlaylistEntry.SingleFile {
        return PlaylistEntry.SingleFile(id, "uri://$id", TrackTags(title = title))
    }

    private fun albumTrack(fileName: String, discTag: Int? = null, trackNumber: Int? = null): AlbumTrack {
        return AlbumTrack(uri = fileName, fileName = fileName, discTag = discTag, trackNumber = trackNumber)
    }
}
