package com.yt8492.asmrplayer.playback

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.MediaItem
import com.yt8492.asmrplayer.playback.PlaybackContract.ACTION_OPEN_PLAYER
import com.yt8492.asmrplayer.playback.PlaybackContract.EXTRA_FOLDER_PATH
import com.yt8492.asmrplayer.playback.PlaybackContract.EXTRA_FOLDER_TITLE
import com.yt8492.asmrplayer.playback.PlaybackContract.EXTRA_PLAYLIST_ID
import com.yt8492.asmrplayer.playback.PlaybackContract.EXTRA_PLAYLIST_NAME
import com.yt8492.asmrplayer.playback.PlaybackContract.EXTRA_QUEUE_TYPE
import com.yt8492.asmrplayer.playback.PlaybackContract.EXTRA_START_INDEX
import com.yt8492.asmrplayer.playback.PlaybackContract.EXTRA_TRACK_ID
import com.yt8492.asmrplayer.playback.PlaybackContract.QUEUE_TYPE_FOLDER
import com.yt8492.asmrplayer.playback.PlaybackContract.QUEUE_TYPE_PLAYLIST
import com.yt8492.asmrplayer.playback.model.PlaybackQueue
import com.yt8492.asmrplayer.playback.model.PlaybackRequest

internal fun PlaybackQueue.toExtras(trackId: Long): Bundle = Bundle().apply {
    putLong(EXTRA_TRACK_ID, trackId)
    when (val queue = this@toExtras) {
        is PlaybackQueue.Playlist -> {
            putString(EXTRA_QUEUE_TYPE, QUEUE_TYPE_PLAYLIST)
            putLong(EXTRA_PLAYLIST_ID, queue.playlistId)
            putString(EXTRA_PLAYLIST_NAME, queue.playlistName)
        }
        is PlaybackQueue.Folder -> {
            putString(EXTRA_QUEUE_TYPE, QUEUE_TYPE_FOLDER)
            putString(EXTRA_FOLDER_PATH, queue.directoryPath)
            putString(EXTRA_FOLDER_TITLE, queue.directoryTitle)
        }
    }
}

internal fun Bundle.toPlaybackQueue(): PlaybackQueue? = when (getString(EXTRA_QUEUE_TYPE)) {
    QUEUE_TYPE_PLAYLIST -> getLong(EXTRA_PLAYLIST_ID, -1).takeIf { it >= 0 }?.let {
        PlaybackQueue.Playlist(it, getString(EXTRA_PLAYLIST_NAME).orEmpty())
    }
    QUEUE_TYPE_FOLDER -> PlaybackQueue.Folder(getString(EXTRA_FOLDER_PATH).orEmpty(), getString(EXTRA_FOLDER_TITLE).orEmpty())
    else -> null
}

internal fun Intent.toPlaybackRequest(): PlaybackRequest? {
    if (action != ACTION_OPEN_PLAYER) return null
    val queue = extras?.toPlaybackQueue() ?: return null
    val trackId = getLongExtra(EXTRA_TRACK_ID, -1).takeIf { it >= 0 } ?: return null
    return PlaybackRequest(queue, trackId, getIntExtra(EXTRA_START_INDEX, -1).takeIf { it >= 0 }, requestId = SystemClock.elapsedRealtime())
}

internal fun Intent.putPlaybackRequest(request: PlaybackRequest): Intent = apply {
    action = ACTION_OPEN_PLAYER
    putExtras(request.queue.toExtras(request.trackId))
    putExtra(EXTRA_START_INDEX, request.startIndex ?: -1)
}

internal fun MediaItem.toPlaybackRequest(index: Int): PlaybackRequest? {
    val queue = mediaMetadata.extras?.toPlaybackQueue() ?: return null
    val trackId = mediaId.toLongOrNull()?.takeIf { it >= 0 } ?: return null
    return PlaybackRequest(queue, trackId, index.takeIf { it >= 0 }, requestId = SystemClock.elapsedRealtime())
}
