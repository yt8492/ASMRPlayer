package com.yt8492.asmrplayer.data.repository

import android.net.Uri
import com.yt8492.asmrplayer.data.model.TrackArtwork
import kotlinx.coroutines.flow.Flow

interface TrackArtworkRepository {
    fun observeTrackArtwork(trackId: Long): Flow<TrackArtwork?>
    suspend fun getTrackArtwork(trackId: Long): TrackArtwork?
    suspend fun isImageUriUsed(imageUri: Uri): Boolean
    suspend fun saveTrackArtwork(trackId: Long, imageUri: Uri)
    suspend fun deleteTrackArtwork(trackId: Long)
}
