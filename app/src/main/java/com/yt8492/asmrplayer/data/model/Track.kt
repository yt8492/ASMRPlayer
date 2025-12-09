package com.yt8492.asmrplayer.data.model

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val trackNumber: Int,
)
