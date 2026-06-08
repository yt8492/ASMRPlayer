package com.yt8492.asmrplayer.navigation

import android.net.Uri

data class PlaybackDestination(
    val queueType: String,
    val albumId: Long,
    val playlistId: Long,
    val trackId: Long,
    val albumTitle: String,
    val albumArtUri: Uri?,
    val playlistName: String,
    val folderPath: String,
    val folderTitle: String,
    val requestId: Long,
)
