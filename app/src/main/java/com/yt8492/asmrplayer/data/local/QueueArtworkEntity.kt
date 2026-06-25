package com.yt8492.asmrplayer.data.local

import androidx.room.Entity

@Entity(
    tableName = "queue_artworks",
    primaryKeys = ["queueType", "queueId"],
)
data class QueueArtworkEntity(
    val queueType: String,
    val queueId: Long,
    val imageUri: String,
    val updatedAt: Long,
)
