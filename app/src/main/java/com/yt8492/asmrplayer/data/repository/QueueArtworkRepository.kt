package com.yt8492.asmrplayer.data.repository

import android.net.Uri
import com.yt8492.asmrplayer.data.model.QueueArtwork
import kotlinx.coroutines.flow.Flow

interface QueueArtworkRepository {
    fun observeQueueArtwork(queueType: String, queueKey: String): Flow<QueueArtwork?>
    suspend fun getQueueArtwork(queueType: String, queueKey: String): QueueArtwork?
    suspend fun isImageUriUsed(imageUri: Uri): Boolean
    suspend fun saveQueueArtwork(queueType: String, queueKey: String, imageUri: Uri)
    suspend fun deleteQueueArtwork(queueType: String, queueKey: String)
}
