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
    override fun observeQueueArtwork(queueType: String, queueId: Long): Flow<QueueArtwork?> {
        return queueArtworkDao.observeQueueArtwork(queueType, queueId).map { it?.toModel() }
    }

    override suspend fun getQueueArtwork(queueType: String, queueId: Long): QueueArtwork? = withContext(Dispatchers.IO) {
        queueArtworkDao.getQueueArtwork(queueType, queueId)?.toModel()
    }

    override suspend fun isImageUriUsed(imageUri: Uri): Boolean = withContext(Dispatchers.IO) {
        queueArtworkDao.countByImageUri(imageUri.toString()) > 0
    }

    override suspend fun saveQueueArtwork(queueType: String, queueId: Long, imageUri: Uri) = withContext(Dispatchers.IO) {
        queueArtworkDao.upsertQueueArtwork(
            QueueArtworkEntity(
                queueType = queueType,
                queueId = queueId,
                imageUri = imageUri.toString(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun deleteQueueArtwork(queueType: String, queueId: Long) = withContext(Dispatchers.IO) {
        queueArtworkDao.deleteQueueArtwork(queueType, queueId)
    }

    private fun QueueArtworkEntity.toModel(): QueueArtwork {
        return QueueArtwork(
            queueType = queueType,
            queueId = queueId,
            imageUri = Uri.parse(imageUri),
        )
    }
}
