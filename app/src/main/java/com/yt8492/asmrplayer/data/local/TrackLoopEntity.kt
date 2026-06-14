package com.yt8492.asmrplayer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_loops")
data class TrackLoopEntity(
    @PrimaryKey
    val trackId: Long,
    val startMs: Long,
    val endMs: Long,
    val updatedAt: Long,
)
