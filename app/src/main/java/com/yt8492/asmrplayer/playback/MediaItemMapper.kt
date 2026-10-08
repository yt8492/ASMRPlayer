package com.yt8492.asmrplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.yt8492.asmrplayer.data.model.Track
import com.yt8492.asmrplayer.playback.model.PlaybackQueue
import com.yt8492.asmrplayer.playback.model.title

internal fun Track.toMediaItem(queue: PlaybackQueue): MediaItem = MediaItem.Builder()
    .setUri(uri)
    .setMediaId(id.toString())
    .setMediaMetadata(MediaMetadata.Builder()
        .setTitle(title).setArtist(artist).setAlbumTitle(albumTitle.ifEmpty { queue.title })
        .setArtworkUri(albumArtUri).setExtras(queue.toExtras(id)).build())
    .build()
