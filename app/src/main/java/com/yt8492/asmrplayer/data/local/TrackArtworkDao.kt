package com.yt8492.asmrplayer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackArtworkDao {
    @Query("SELECT * FROM track_artworks WHERE trackId = :trackId")
    fun observeTrackArtwork(trackId: Long): Flow<TrackArtworkEntity?>

    @Query("SELECT * FROM track_artworks WHERE trackId = :trackId")
    suspend fun getTrackArtwork(trackId: Long): TrackArtworkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrackArtwork(trackArtwork: TrackArtworkEntity)

    @Query("DELETE FROM track_artworks WHERE trackId = :trackId")
    suspend fun deleteTrackArtwork(trackId: Long)
}
