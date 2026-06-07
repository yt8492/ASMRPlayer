package com.yt8492.asmrplayer.navigation

import android.net.Uri

data class PlaybackDestination(
    val albumId: Long,
    val trackId: Long,
    val albumTitle: String,
    val albumArtUri: Uri?,
    val requestId: Long,
)
