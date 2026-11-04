package com.exoplanet.notch

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

data class TrackTags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
)

object TrackMetadataReader {
    fun parseCount(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val head = raw.trim().substringBefore('/').trim()
        return head.toIntOrNull()?.takeIf { it > 0 }
    }

    fun read(context: Context, uri: Uri): TrackTags {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            TrackTags(
                title = text(retriever, MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = text(retriever, MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = text(retriever, MediaMetadataRetriever.METADATA_KEY_ALBUM),
                trackNumber = parseCount(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)),
                discNumber = parseCount(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)),
            )
        } catch (_: Exception) {
            TrackTags()
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun text(retriever: MediaMetadataRetriever, key: Int): String? {
        return retriever.extractMetadata(key)?.trim()?.ifEmpty { null }
    }
}
