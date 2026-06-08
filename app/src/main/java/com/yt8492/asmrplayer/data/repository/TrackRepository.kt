package com.yt8492.asmrplayer.data.repository

import com.yt8492.asmrplayer.data.model.Track

interface TrackRepository {
    suspend fun getTracks(albumId: Long): List<Track>
    suspend fun getTracks(trackIds: List<Long>): List<Track>
}
