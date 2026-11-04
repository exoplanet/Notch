package com.exoplanet.notch

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

fun FlatTrack.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder().setTitle(title)
    artist?.takeIf { it.isNotBlank() }?.let { metadata.setArtist(it) }
    album?.takeIf { it.isNotBlank() }?.let { metadata.setAlbumTitle(it) }
    trackNumber?.let { metadata.setTrackNumber(it) }
    discNumber?.let { metadata.setDiscNumber(it) }
    return MediaItem.Builder()
        .setMediaId("$entryId#$trackIndex")
        .setUri(uri)
        .setMediaMetadata(metadata.build())
        .build()
}
