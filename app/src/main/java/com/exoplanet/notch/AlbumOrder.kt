package com.exoplanet.notch

data class AlbumTrack(
    val uri: String,
    val fileName: String,
    val relativeFolder: String = "",
    val discTag: Int? = null,
    val trackNumber: Int? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
)

object AlbumOrder {
    private val discSegment = Regex("""(?i)^(?:cd|disc)\s*0*(\d+)$""")

    fun sort(tracks: List<AlbumTrack>): List<AlbumTrack> {
        return tracks.withIndex().sortedWith { left, right ->
            val byDisc = discRank(left.value).compareTo(discRank(right.value))
            if (byDisc != 0) return@sortedWith byDisc
            val byTrack = trackRank(left.value).compareTo(trackRank(right.value))
            if (byTrack != 0) return@sortedWith byTrack
            val byName = naturalCompare(left.value.fileName, right.value.fileName)
            if (byName != 0) return@sortedWith byName
            left.index.compareTo(right.index)
        }.map { it.value }
    }

    fun discFromFolder(relativeFolder: String): Int? {
        if (relativeFolder.isBlank()) return null
        for (segment in relativeFolder.split('/', '\\')) {
            val match = discSegment.matchEntire(segment.trim()) ?: continue
            return match.groupValues[1].toIntOrNull()?.takeIf { it > 0 }
        }
        return null
    }

    fun effectiveDisc(track: AlbumTrack): Int? = track.discTag ?: discFromFolder(track.relativeFolder)

    fun naturalCompare(left: String, right: String): Int {
        var i = 0
        var j = 0
        while (i < left.length && j < right.length) {
            if (left[i].isDigit() && right[j].isDigit()) {
                val leftStart = i
                val rightStart = j
                while (i < left.length && left[i].isDigit()) i++
                while (j < right.length && right[j].isDigit()) j++
                val compared = compareNumeric(left.substring(leftStart, i), right.substring(rightStart, j))
                if (compared != 0) return compared
            } else {
                val leftStart = i
                val rightStart = j
                while (i < left.length && !left[i].isDigit()) i++
                while (j < right.length && !right[j].isDigit()) j++
                val compared = left.substring(leftStart, i).compareTo(right.substring(rightStart, j), ignoreCase = true)
                if (compared != 0) return compared
            }
        }
        return (left.length - i).compareTo(right.length - j)
    }

    private fun compareNumeric(leftDigits: String, rightDigits: String): Int {
        val left = leftDigits.trimStart('0').ifEmpty { "0" }
        val right = rightDigits.trimStart('0').ifEmpty { "0" }
        val byLength = left.length.compareTo(right.length)
        if (byLength != 0) return byLength
        return left.compareTo(right)
    }

    private fun trackRank(track: AlbumTrack): Int = track.trackNumber ?: Int.MAX_VALUE

    private fun discRank(track: AlbumTrack): DiscRank {
        val number = effectiveDisc(track)
        return if (number != null) DiscRank.Numbered(number) else DiscRank.Named(track.relativeFolder)
    }

    private sealed interface DiscRank : Comparable<DiscRank> {
        data class Numbered(val number: Int) : DiscRank
        data class Named(val folder: String) : DiscRank

        override fun compareTo(other: DiscRank): Int {
            return when {
                this is Numbered && other is Numbered -> number.compareTo(other.number)
                this is Numbered -> -1
                other is Numbered -> 1
                this is Named && other is Named -> naturalCompare(folder, other.folder)
                else -> 0
            }
        }
    }
}
