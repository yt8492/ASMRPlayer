package com.yt8492.asmrplayer.data.repository

import android.net.Uri
import com.yt8492.asmrplayer.data.model.QueueArtworkTarget
import kotlinx.coroutines.flow.Flow

interface ArtworkRepository {
    fun observeTrackArtwork(trackId: Long): Flow<Uri?>
    fun observeQueueArtwork(target: QueueArtworkTarget): Flow<Uri?>
    fun observeResolvedArtwork(trackId: Long, target: QueueArtworkTarget, fallback: Uri?): Flow<Uri?>
    suspend fun saveTrackArtwork(trackId: Long, uri: Uri)
    suspend fun deleteTrackArtwork(trackId: Long)
    suspend fun saveQueueArtwork(target: QueueArtworkTarget, uri: Uri)
    suspend fun deleteQueueArtwork(target: QueueArtworkTarget)
}
