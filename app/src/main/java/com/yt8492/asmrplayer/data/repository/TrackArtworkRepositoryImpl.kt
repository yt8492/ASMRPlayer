package com.yt8492.asmrplayer.data.repository

import android.net.Uri
import com.yt8492.asmrplayer.data.local.TrackArtworkDao
import com.yt8492.asmrplayer.data.local.TrackArtworkEntity
import com.yt8492.asmrplayer.data.model.TrackArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TrackArtworkRepositoryImpl(
    private val trackArtworkDao: TrackArtworkDao,
) : TrackArtworkRepository {
    override fun observeTrackArtwork(trackId: Long): Flow<TrackArtwork?> {
        return trackArtworkDao.observeTrackArtwork(trackId).map { it?.toModel() }
    }

    override suspend fun getTrackArtwork(trackId: Long): TrackArtwork? = withContext(Dispatchers.IO) {
        trackArtworkDao.getTrackArtwork(trackId)?.toModel()
    }

    override suspend fun isImageUriUsed(imageUri: Uri): Boolean = withContext(Dispatchers.IO) {
        trackArtworkDao.countByImageUri(imageUri.toString()) > 0
    }

    override suspend fun saveTrackArtwork(trackId: Long, imageUri: Uri) = withContext(Dispatchers.IO) {
        trackArtworkDao.upsertTrackArtwork(
            TrackArtworkEntity(
                trackId = trackId,
                imageUri = imageUri.toString(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun deleteTrackArtwork(trackId: Long) = withContext(Dispatchers.IO) {
        trackArtworkDao.deleteTrackArtwork(trackId)
    }

    private fun TrackArtworkEntity.toModel(): TrackArtwork {
        return TrackArtwork(
            trackId = trackId,
            imageUri = Uri.parse(imageUri),
        )
    }
}
