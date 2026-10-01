package com.yt8492.asmrplayer.ui.player

sealed interface PlaybackQueue {
    data class Playlist(
        val playlistId: Long,
        val playlistName: String,
    ) : PlaybackQueue

    data class Folder(
        val directoryPath: String,
        val directoryTitle: String,
    ) : PlaybackQueue
}

internal fun PlaybackQueue.logType(): String {
    return when (this) {
        is PlaybackQueue.Playlist -> "playlist"
        is PlaybackQueue.Folder -> "folder"
    }
}
