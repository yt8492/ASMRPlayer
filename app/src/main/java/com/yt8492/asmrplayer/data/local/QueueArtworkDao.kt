package com.yt8492.asmrplayer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueArtworkDao {
    @Query("SELECT * FROM queue_artworks WHERE queueType = :queueType AND queueKey = :queueKey")
    fun observeQueueArtwork(queueType: String, queueKey: String): Flow<QueueArtworkEntity?>

    @Query("SELECT * FROM queue_artworks WHERE queueType = :queueType AND queueKey = :queueKey")
    suspend fun getQueueArtwork(queueType: String, queueKey: String): QueueArtworkEntity?

    @Query("SELECT COUNT(*) FROM queue_artworks WHERE imageUri = :imageUri")
    suspend fun countByImageUri(imageUri: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQueueArtwork(queueArtwork: QueueArtworkEntity)

    @Query("DELETE FROM queue_artworks WHERE queueType = :queueType AND queueKey = :queueKey")
    suspend fun deleteQueueArtwork(queueType: String, queueKey: String)
}
