package com.yt8492.asmrplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yt8492.asmrplayer.data.local.entity.TrackLoopEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackLoopDao {
    @Query("SELECT * FROM track_loops WHERE trackId = :trackId")
    fun observeTrackLoop(trackId: Long): Flow<TrackLoopEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrackLoop(trackLoop: TrackLoopEntity)

    @Query("DELETE FROM track_loops WHERE trackId = :trackId")
    suspend fun deleteTrackLoop(trackId: Long)
}
