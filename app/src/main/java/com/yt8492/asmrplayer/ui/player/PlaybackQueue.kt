package com.yt8492.asmrplayer.ui.player

sealed interface PlaybackQueue {
    data class Album(
        val albumId: Long,
        val albumTitle: String,
        val albumArtUri: android.net.Uri?,
    ) : PlaybackQueue

    data class Playlist(
        val playlistId: Long,
        val playlistName: String,
    ) : PlaybackQueue

    data class Folder(
        val directoryPath: String,
        val directoryTitle: String,
    ) : PlaybackQueue
}
