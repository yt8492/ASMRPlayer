package com.yt8492.asmrplayer.navigation

import android.net.Uri
import com.yt8492.asmrplayer.playback.model.PlaybackQueue
import com.yt8492.asmrplayer.playback.model.PlaybackRequest

internal fun PlaybackRequest.toPlayerRoute(): String = when (val queue = queue) {
    is PlaybackQueue.Playlist -> "player/playlist/${queue.playlistId}/$trackId?name=${Uri.encode(queue.playlistName)}" +
        "&playlistTrackId=${playlistTrackId ?: -1}" + (startIndex?.let { "&startIndex=$it" }.orEmpty())
    is PlaybackQueue.Folder -> "player/folder/$trackId?path=${Uri.encode(queue.directoryPath)}&title=${Uri.encode(queue.directoryTitle)}"
}
