package com.yt8492.asmrplayer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueArtworkDao {
    @Query("SELECT * FROM queue_artworks WHERE queueType = :queueType AND queueId = :queueId")
    fun observeQueueArtwork(queueType: String, queueId: Long): Flow<QueueArtworkEntity?>

    @Query("SELECT * FROM queue_artworks WHERE queueType = :queueType AND queueId = :queueId")
    suspend fun getQueueArtwork(queueType: String, queueId: Long): QueueArtworkEntity?

    @Query("SELECT COUNT(*) FROM queue_artworks WHERE imageUri = :imageUri")
    suspend fun countByImageUri(imageUri: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQueueArtwork(queueArtwork: QueueArtworkEntity)

    @Query("DELETE FROM queue_artworks WHERE queueType = :queueType AND queueId = :queueId")
    suspend fun deleteQueueArtwork(queueType: String, queueId: Long)
}
