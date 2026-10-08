package com.yt8492.asmrplayer.playback.model

/** 通知・ミニプレイヤー・画面遷移で共有する再生要求。行IDで重複曲を区別する。 */
data class PlaybackRequest(
    val queue: PlaybackQueue,
    val trackId: Long,
    val startIndex: Int? = null,
    val playlistTrackId: Long? = null,
    val requestId: Long = 0,
)
