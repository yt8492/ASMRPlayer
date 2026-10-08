package com.yt8492.asmrplayer.data.repository.impl

import android.net.Uri
import com.yt8492.asmrplayer.data.local.dao.TrackArtworkDao
import com.yt8492.asmrplayer.data.local.entity.TrackArtworkEntity
import com.yt8492.asmrplayer.data.model.TrackArtwork
import com.yt8492.asmrplayer.data.repository.TrackArtworkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class TrackArtworkRepositoryImpl(
    private val trackArtworkDao: TrackArtworkDao,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) : TrackArtworkRepository {
    override fun observeTrackArtwork(trackId: Long): Flow<TrackArtwork?> {
        return trackArtworkDao.observeTrackArtwork(trackId).map { it?.toModel() }
    }

    override suspend fun getTrackArtwork(trackId: Long): TrackArtwork? = withContext(ioDispatcher) {
        trackArtworkDao.getTrackArtwork(trackId)?.toModel()
    }

    override suspend fun isImageUriUsed(imageUri: Uri): Boolean = withContext(ioDispatcher) {
        trackArtworkDao.isImageUriUsed(imageUri.toString())
    }

    override suspend fun saveTrackArtwork(trackId: Long, imageUri: Uri) = withContext(ioDispatcher) {
        trackArtworkDao.upsertTrackArtwork(
            TrackArtworkEntity(
                trackId = trackId,
                imageUri = imageUri.toString(),
                updatedAt = now(),
            ),
        )
    }

    override suspend fun deleteTrackArtwork(trackId: Long) = withContext(ioDispatcher) {
        trackArtworkDao.deleteTrackArtwork(trackId)
    }

    private fun TrackArtworkEntity.toModel(): TrackArtwork {
        return TrackArtwork(
            trackId = trackId,
            imageUri = Uri.parse(imageUri),
        )
    }
}
