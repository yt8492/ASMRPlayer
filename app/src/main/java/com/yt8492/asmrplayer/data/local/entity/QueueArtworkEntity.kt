package com.yt8492.asmrplayer.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "queue_artworks",
    primaryKeys = ["queueType", "queueKey"],
)
data class QueueArtworkEntity(
    val queueType: String,
    val queueKey: String,
    val imageUri: String,
    val updatedAt: Long,
)
