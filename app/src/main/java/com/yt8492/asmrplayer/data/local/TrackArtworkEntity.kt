package com.yt8492.asmrplayer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_artworks")
data class TrackArtworkEntity(
    @PrimaryKey
    val trackId: Long,
    val imageUri: String,
    val updatedAt: Long,
)
