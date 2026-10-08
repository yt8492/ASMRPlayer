package com.yt8492.asmrplayer.data.repository.impl

import android.net.Uri
import com.yt8492.asmrplayer.data.local.dao.QueueArtworkDao
import com.yt8492.asmrplayer.data.local.entity.QueueArtworkEntity
import com.yt8492.asmrplayer.data.model.QueueArtwork
import com.yt8492.asmrplayer.data.repository.QueueArtworkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class QueueArtworkRepositoryImpl(
    private val queueArtworkDao: QueueArtworkDao,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) : QueueArtworkRepository {
    override fun observeQueueArtwork(queueType: String, queueKey: String): Flow<QueueArtwork?> {
        return queueArtworkDao.observeQueueArtwork(queueType, queueKey).map { it?.toModel() }
    }

    override suspend fun getQueueArtwork(queueType: String, queueKey: String): QueueArtwork? = withContext(ioDispatcher) {
        queueArtworkDao.getQueueArtwork(queueType, queueKey)?.toModel()
    }

    override suspend fun isImageUriUsed(imageUri: Uri): Boolean = withContext(ioDispatcher) {
        queueArtworkDao.countByImageUri(imageUri.toString()) > 0
    }

    override suspend fun saveQueueArtwork(queueType: String, queueKey: String, imageUri: Uri) = withContext(ioDispatcher) {
        queueArtworkDao.upsertQueueArtwork(
            QueueArtworkEntity(
                queueType = queueType,
                queueKey = queueKey,
                imageUri = imageUri.toString(),
                updatedAt = now(),
            ),
        )
    }

    override suspend fun deleteQueueArtwork(queueType: String, queueKey: String) = withContext(ioDispatcher) {
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
