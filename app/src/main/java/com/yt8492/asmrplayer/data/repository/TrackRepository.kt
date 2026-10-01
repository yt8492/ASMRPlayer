package com.yt8492.asmrplayer.data.repository

import com.yt8492.asmrplayer.data.model.Track

interface TrackRepository {
    suspend fun getTracks(trackIds: List<Long>): List<Track>
    suspend fun getTracksInDirectory(directoryPath: String): List<Track>
}
