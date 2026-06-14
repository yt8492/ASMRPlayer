package com.yt8492.asmrplayer.data.repository

import com.yt8492.asmrplayer.data.model.TrackLoop
import kotlinx.coroutines.flow.Flow

interface TrackLoopRepository {
    fun observeTrackLoop(trackId: Long): Flow<TrackLoop?>
    suspend fun saveTrackLoop(trackId: Long, startMs: Long, endMs: Long)
    suspend fun deleteTrackLoop(trackId: Long)
}
