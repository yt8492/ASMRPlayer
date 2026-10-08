package com.yt8492.asmrplayer.data.repository.impl

import com.yt8492.asmrplayer.data.local.dao.TrackLoopDao
import com.yt8492.asmrplayer.data.local.entity.TrackLoopEntity
import com.yt8492.asmrplayer.data.model.TrackLoop
import com.yt8492.asmrplayer.data.repository.TrackLoopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class TrackLoopRepositoryImpl(
    private val trackLoopDao: TrackLoopDao,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) : TrackLoopRepository {
    override fun observeTrackLoop(trackId: Long): Flow<TrackLoop?> {
        return trackLoopDao.observeTrackLoop(trackId).map { it?.toModel() }
    }

    override suspend fun saveTrackLoop(trackId: Long, startMs: Long, endMs: Long) = withContext(ioDispatcher) {
        trackLoopDao.upsertTrackLoop(
            TrackLoopEntity(
                trackId = trackId,
                startMs = startMs,
                endMs = endMs,
                updatedAt = now(),
            ),
        )
    }

    override suspend fun deleteTrackLoop(trackId: Long) = withContext(ioDispatcher) {
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
