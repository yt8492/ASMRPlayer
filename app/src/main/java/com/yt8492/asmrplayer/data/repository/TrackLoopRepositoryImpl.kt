package com.yt8492.asmrplayer.data.repository

import com.yt8492.asmrplayer.data.local.TrackLoopDao
import com.yt8492.asmrplayer.data.local.TrackLoopEntity
import com.yt8492.asmrplayer.data.model.TrackLoop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TrackLoopRepositoryImpl(
    private val trackLoopDao: TrackLoopDao,
) : TrackLoopRepository {
    override fun observeTrackLoop(trackId: Long): Flow<TrackLoop?> {
        return trackLoopDao.observeTrackLoop(trackId).map { it?.toModel() }
    }

    override suspend fun saveTrackLoop(trackId: Long, startMs: Long, endMs: Long) = withContext(Dispatchers.IO) {
        trackLoopDao.upsertTrackLoop(
            TrackLoopEntity(
                trackId = trackId,
                startMs = startMs,
                endMs = endMs,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun deleteTrackLoop(trackId: Long) = withContext(Dispatchers.IO) {
        trackLoopDao.deleteTrackLoop(trackId)
    }

    private fun TrackLoopEntity.toModel(): TrackLoop {
        return TrackLoop(
            trackId = trackId,
            startMs = startMs,
            endMs = endMs,
        )
    }
}
