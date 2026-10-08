package com.yt8492.asmrplayer.playback.model

import com.yt8492.asmrplayer.data.model.QueueArtworkTarget

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

fun PlaybackQueue.artworkTarget(): QueueArtworkTarget = when (this) {
    is PlaybackQueue.Playlist -> QueueArtworkTarget.Playlist(playlistId)
    is PlaybackQueue.Folder -> QueueArtworkTarget.Folder(directoryPath)
}

val PlaybackQueue.title: String
    get() = when (this) {
        is PlaybackQueue.Playlist -> playlistName
        is PlaybackQueue.Folder -> directoryTitle
    }
