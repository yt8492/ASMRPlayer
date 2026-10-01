package com.yt8492.asmrplayer.navigation

data class PlaybackDestination(
    val queueType: String,
    val playlistId: Long,
    val trackId: Long,
    val startIndex: Int?,
    val playlistName: String,
    val folderPath: String,
    val folderTitle: String,
    val requestId: Long,
)
