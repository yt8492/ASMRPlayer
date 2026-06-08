package com.yt8492.asmrplayer.data.model

import android.net.Uri

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val albumId: Long? = null,
    val albumTitle: String = "",
    val albumArtUri: Uri? = null,
    val durationMs: Long,
    val trackNumber: Int,
    val uri: Uri,
)
