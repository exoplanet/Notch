package com.exoplanet.notch

import java.util.UUID

class Playlist(
    val entries: List<PlaylistEntry> = emptyList(),
) {
    fun appendAll(more: List<PlaylistEntry>): Playlist {
        if (more.isEmpty()) return this
        return Playlist(entries + more)
    }

    fun appendAlbum(album: PlaylistEntry.Album): Playlist {
        val sorted = album.copy(tracks = AlbumOrder.sort(album.tracks))
        if (sorted.tracks.isEmpty()) return this
        return Playlist(entries + sorted)
    }

    fun moveEntry(from: Int, to: Int): Playlist {
        if (from == to || from !in entries.indices || to !in entries.indices) return this
        val mutable = entries.toMutableList()
        val entry = mutable.removeAt(from)
        mutable.add(to, entry)
        return Playlist(mutable)
    }

    fun removeEntry(entryId: String): Playlist {
        return Playlist(entries.filterNot { it.id == entryId })
    }

    fun removeTrack(entryId: String, trackIndex: Int): Playlist {
        val updated = entries.mapNotNull { entry ->
            if (entry.id != entryId) return@mapNotNull entry
            when (entry) {
                is PlaylistEntry.SingleFile -> null
                is PlaylistEntry.Album -> {
                    if (trackIndex !in entry.tracks.indices) {
                        entry
                    } else {
                        val tracks = entry.tracks.filterIndexed { index, _ -> index != trackIndex }
                        if (tracks.isEmpty()) null else entry.copy(tracks = tracks)
                    }
                }
            }
        }
        return Playlist(updated)
    }

    fun flatten(): List<FlatTrack> {
        val out = mutableListOf<FlatTrack>()
        entries.forEachIndexed { entryIndex, entry ->
            when (entry) {
                is PlaylistEntry.SingleFile -> if (!entry.unavailable) {
                    out += entry.toFlat(entryIndex, out.size)
                }
                is PlaylistEntry.Album -> entry.tracks.forEachIndexed { trackIndex, track ->
                    if (!track.unavailable) out += track.toFlat(entry, entryIndex, trackIndex, out.size)
                }
            }
        }
        return out
    }

    fun mediaStart(entryIndex: Int): Int {
        return entries.take(entryIndex).sumOf { it.mediaCount }
    }

    fun mediaRange(entryIndex: Int): IntRange {
        val start = mediaStart(entryIndex)
        return start until start + entries[entryIndex].mediaCount
    }

    fun mediaMove(fromEntry: Int, toEntry: Int): MediaMove? {
        if (fromEntry == toEntry || fromEntry !in entries.indices || toEntry !in entries.indices) return null
        val start = mediaStart(fromEntry)
        val count = entries[fromEntry].mediaCount
        val newIndex = if (toEntry > fromEntry) {
            mediaStart(toEntry) + entries[toEntry].mediaCount - count
        } else {
            mediaStart(toEntry)
        }
        return MediaMove(fromIndex = start, toIndexExclusive = start + count, newIndex = newIndex)
    }

    fun locationAt(mediaIndex: Int): PlaylistLocation? {
        if (mediaIndex < 0) return null
        var cursor = 0
        entries.forEachIndexed { entryIndex, entry ->
            val count = entry.mediaCount
            if (mediaIndex < cursor + count) {
                return PlaylistLocation(
                    entryId = entry.id,
                    entryIndex = entryIndex,
                    trackIndex = mediaIndex - cursor,
                    mediaIndex = mediaIndex,
                )
            }
            cursor += count
        }
        return null
    }

    companion object {
        fun positionLabel(index: Int, count: Int): String? {
            if (count <= 0) return null
            val n = index.coerceIn(0, count - 1) + 1
            return "$n / $count"
        }

        fun trackTitle(metadataTitle: CharSequence?, uriPath: String?): String {
            val fromMeta = metadataTitle?.toString()?.trim().orEmpty()
            if (fromMeta.isNotEmpty()) return fromMeta
            val fromPath = uriPath?.trim().orEmpty()
            if (fromPath.isNotEmpty()) return fromPath
            return "Audio"
        }
    }
}

sealed class PlaylistEntry {
    abstract val id: String
    abstract val mediaCount: Int

    /** A queue copy. Saved playlists keep their own ids, so the same playlist can be queued twice. */
    fun withNewId(): PlaylistEntry {
        val next = UUID.randomUUID().toString()
        return when (this) {
            is SingleFile -> copy(id = next)
            is Album -> copy(id = next)
        }
    }

    data class SingleFile(
        override val id: String,
        val uri: String,
        val metadata: TrackTags,
        val unavailable: Boolean = false,
    ) : PlaylistEntry() {
        override val mediaCount: Int
            get() = if (unavailable) 0 else 1
    }

    data class Album(
        override val id: String,
        val treeUri: String,
        val title: String,
        val tracks: List<AlbumTrack>,
    ) : PlaylistEntry() {
        override val mediaCount: Int
            get() = tracks.count { !it.unavailable }
    }
}

data class FlatTrack(
    val entryId: String,
    val entryIndex: Int,
    val trackIndex: Int,
    val mediaIndex: Int,
    val uri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
)

data class MediaMove(
    val fromIndex: Int,
    val toIndexExclusive: Int,
    val newIndex: Int,
)

data class PlaylistLocation(
    val entryId: String,
    val entryIndex: Int,
    val trackIndex: Int,
    val mediaIndex: Int,
)

private fun PlaylistEntry.SingleFile.toFlat(entryIndex: Int, mediaIndex: Int): FlatTrack {
    val title = metadata.title?.takeIf { it.isNotBlank() } ?: uri.substringAfterLast('/')
    return FlatTrack(
        entryId = id,
        entryIndex = entryIndex,
        trackIndex = 0,
        mediaIndex = mediaIndex,
        uri = uri,
        title = title.ifBlank { "Audio" },
        artist = metadata.artist,
        album = metadata.album,
        trackNumber = metadata.trackNumber,
        discNumber = metadata.discNumber,
    )
}

private fun AlbumTrack.toFlat(
    entry: PlaylistEntry.Album,
    entryIndex: Int,
    trackIndex: Int,
    mediaIndex: Int,
): FlatTrack {
    return FlatTrack(
        entryId = entry.id,
        entryIndex = entryIndex,
        trackIndex = trackIndex,
        mediaIndex = mediaIndex,
        uri = uri,
        title = title?.takeIf { it.isNotBlank() } ?: fileName,
        artist = artist,
        album = album ?: entry.title,
        trackNumber = trackNumber,
        discNumber = AlbumOrder.effectiveDisc(this),
    )
}
