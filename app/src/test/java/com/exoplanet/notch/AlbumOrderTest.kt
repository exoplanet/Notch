package com.exoplanet.notch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumOrderTest {
    @Test
    fun multiDiscTagsPlayDiscOneBeforeDiscTwo() {
        val sorted = AlbumOrder.sort(
            listOf(
                track("b.flac", discTag = 2, trackNumber = 1),
                track("a.flac", discTag = 1, trackNumber = 2),
                track("c.flac", discTag = 1, trackNumber = 1),
            ),
        )
        assertEquals(listOf("c.flac", "a.flac", "b.flac"), sorted.map { it.fileName })
    }

    @Test
    fun multiDiscFoldersPlayCd1BeforeDisc2() {
        val sorted = AlbumOrder.sort(
            listOf(
                track("01.flac", relativeFolder = "Disc 2"),
                track("02.flac", relativeFolder = "CD1"),
                track("01.flac", relativeFolder = "CD1"),
            ),
        )
        assertEquals(listOf("CD1", "CD1", "Disc 2"), sorted.map { it.relativeFolder })
        assertEquals(listOf("01.flac", "02.flac", "01.flac"), sorted.map { it.fileName })
    }

    @Test
    fun slashTrackNumberKeepsTheNumberBeforeTheSlash() {
        assertEquals(3, TrackMetadataReader.parseCount("3/12"))
        assertEquals(3, TrackMetadataReader.parseCount(" 03 / 12 "))
        assertEquals(1, TrackMetadataReader.parseCount("1/2"))
        assertNull(TrackMetadataReader.parseCount(null))
        assertNull(TrackMetadataReader.parseCount("  "))
        assertNull(TrackMetadataReader.parseCount("nope"))
        assertNull(TrackMetadataReader.parseCount("0/12"))
        val sorted = AlbumOrder.sort(
            listOf(
                track("later.flac", trackNumber = TrackMetadataReader.parseCount("3/12")),
                track("first.flac", trackNumber = TrackMetadataReader.parseCount("1/12")),
            ),
        )
        assertEquals(listOf("first.flac", "later.flac"), sorted.map { it.fileName })
    }

    @Test
    fun missingTrackNumbersSortAfterNumberedTracks() {
        val sorted = AlbumOrder.sort(
            listOf(
                track("b.flac", trackNumber = 2),
                track("a.flac", trackNumber = null),
                track("c.flac", trackNumber = 1),
            ),
        )
        assertEquals(listOf("c.flac", "b.flac", "a.flac"), sorted.map { it.fileName })
    }

    @Test
    fun fileNamesSortNaturallySoTwoPrecedesTen() {
        assertEquals(-1, AlbumOrder.naturalCompare("2.flac", "10.flac").coerceIn(-1, 1))
        val sorted = AlbumOrder.sort(
            listOf(
                track("10.flac"),
                track("2.flac"),
                track("1.flac"),
            ),
        )
        assertEquals(listOf("1.flac", "2.flac", "10.flac"), sorted.map { it.fileName })
    }

    @Test
    fun tagDiscWinsOverTheFolderName() {
        val sorted = AlbumOrder.sort(
            listOf(
                track("from-folder.flac", relativeFolder = "CD2"),
                track("from-tag.flac", relativeFolder = "CD9", discTag = 1, trackNumber = 1),
            ),
        )
        assertEquals(listOf("from-tag.flac", "from-folder.flac"), sorted.map { it.fileName })
    }

    @Test
    fun foldersWithoutDiscNumbersSortNaturally() {
        val sorted = AlbumOrder.sort(
            listOf(
                track("live.flac", relativeFolder = "Live", trackNumber = 1),
                track("bonus.flac", relativeFolder = "Bonus", trackNumber = 1),
            ),
        )
        assertEquals(listOf("Bonus", "Live"), sorted.map { it.relativeFolder })
    }

    private fun track(
        fileName: String,
        relativeFolder: String = "",
        discTag: Int? = null,
        trackNumber: Int? = null,
    ): AlbumTrack {
        return AlbumTrack(
            uri = fileName,
            fileName = fileName,
            relativeFolder = relativeFolder,
            discTag = discTag,
            trackNumber = trackNumber,
        )
    }
}
