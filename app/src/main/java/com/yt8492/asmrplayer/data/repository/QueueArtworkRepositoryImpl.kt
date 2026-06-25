package com.yt8492.asmrplayer.data.repository

import android.net.Uri
import com.yt8492.asmrplayer.data.local.QueueArtworkDao
import com.yt8492.asmrplayer.data.local.QueueArtworkEntity
import com.yt8492.asmrplayer.data.model.QueueArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class QueueArtworkRepositoryImpl(
    private val queueArtworkDao: QueueArtworkDao,
) : QueueArtworkRepository {
    override fun observeQueueArtwork(queueType: String, queueKey: String): Flow<QueueArtwork?> {
        return queueArtworkDao.observeQueueArtwork(queueType, queueKey).map { it?.toModel() }
    }

    override suspend fun getQueueArtwork(queueType: String, queueKey: String): QueueArtwork? = withContext(Dispatchers.IO) {
        queueArtworkDao.getQueueArtwork(queueType, queueKey)?.toModel()
    }

    override suspend fun isImageUriUsed(imageUri: Uri): Boolean = withContext(Dispatchers.IO) {
        queueArtworkDao.countByImageUri(imageUri.toString()) > 0
    }

    override suspend fun saveQueueArtwork(queueType: String, queueKey: String, imageUri: Uri) = withContext(Dispatchers.IO) {
        queueArtworkDao.upsertQueueArtwork(
            QueueArtworkEntity(
                queueType = queueType,
                queueKey = queueKey,
                imageUri = imageUri.toString(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun deleteQueueArtwork(queueType: String, queueKey: String) = withContext(Dispatchers.IO) {
        queueArtworkDao.deleteQueueArtwork(queueType, queueKey)
    }

    private fun QueueArtworkEntity.toModel(): QueueArtwork {
        return QueueArtwork(
            queueType = queueType,
            queueKey = queueKey,
            imageUri = Uri.parse(imageUri),
        )
    }
}
