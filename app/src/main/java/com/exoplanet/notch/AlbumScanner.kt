package com.exoplanet.notch

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AlbumScanner {
    private val audioExtensions = setOf(
        "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "aiff", "aif", "wma", "alac", "oga", "wv", "ape", "m4b",
    )

    suspend fun collect(context: Context, treeUri: Uri): List<AlbumTrack> = withContext(Dispatchers.IO) {
        readTracks(context, treeUri)
    }

    /**
     * Ok means the tree could be listed, even when it contains no audio.
     * Failed means the grant or the document could not be read, so a saved cache should be kept.
     */
    fun scan(context: Context, treeUri: Uri): AlbumScanResult {
        return try {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return AlbumScanResult.Failed
            try {
                root.listFiles()
            } catch (_: Exception) {
                return AlbumScanResult.Failed
            }
            AlbumScanResult.Ok(readTracks(context, treeUri))
        } catch (_: Exception) {
            AlbumScanResult.Failed
        }
    }

    private fun readTracks(context: Context, treeUri: Uri): List<AlbumTrack> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val found = mutableListOf<AlbumTrack>()
        walk(context, root, "", found)
        return found
    }

    private fun walk(context: Context, document: DocumentFile, relativeFolder: String, into: MutableList<AlbumTrack>) {
        val children = try {
            document.listFiles()
        } catch (_: Exception) {
            return
        }
        for (child in children) {
            if (child.isDirectory) {
                val name = child.name?.trim().orEmpty().ifEmpty { "folder" }
                val next = if (relativeFolder.isEmpty()) name else "$relativeFolder/$name"
                walk(context, child, next, into)
            } else if (isAudio(child.name, child.type)) {
                val tags = try {
                    TrackMetadataReader.read(context, child.uri)
                } catch (_: Exception) {
                    TrackTags()
                }
                into += AlbumTrack(
                    uri = child.uri.toString(),
                    fileName = child.name?.trim().orEmpty().ifEmpty { "audio" },
                    relativeFolder = relativeFolder,
                    discTag = tags.discNumber,
                    trackNumber = tags.trackNumber,
                    title = tags.title,
                    artist = tags.artist,
                    album = tags.album,
                )
            }
        }
    }

    fun isAudio(name: String?, mime: String?): Boolean {
        if (mime != null && mime.startsWith("video/")) return false
        if (mime != null && mime.startsWith("audio/")) return true
        val extension = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return extension in audioExtensions
    }
}

sealed class AlbumScanResult {
    data class Ok(val tracks: List<AlbumTrack>) : AlbumScanResult()
    data object Failed : AlbumScanResult()
}

fun AlbumTrack.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title?.takeIf { it.isNotBlank() } ?: fileName)
    artist?.takeIf { it.isNotBlank() }?.let { metadata.setArtist(it) }
    album?.takeIf { it.isNotBlank() }?.let { metadata.setAlbumTitle(it) }
    trackNumber?.let { metadata.setTrackNumber(it) }
    AlbumOrder.effectiveDisc(this)?.let { metadata.setDiscNumber(it) }
    return MediaItem.Builder()
        .setUri(uri)
        .setMediaMetadata(metadata.build())
        .build()
}
