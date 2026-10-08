package com.yt8492.asmrplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yt8492.asmrplayer.data.local.entity.TrackArtworkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackArtworkDao {
    @Query("SELECT * FROM track_artworks WHERE trackId = :trackId")
    fun observeTrackArtwork(trackId: Long): Flow<TrackArtworkEntity?>

    @Query("SELECT * FROM track_artworks WHERE trackId = :trackId")
    suspend fun getTrackArtwork(trackId: Long): TrackArtworkEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM track_artworks WHERE imageUri = :imageUri)")
    suspend fun isImageUriUsed(imageUri: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrackArtwork(trackArtwork: TrackArtworkEntity)

    @Query("DELETE FROM track_artworks WHERE trackId = :trackId")
    suspend fun deleteTrackArtwork(trackId: Long)
}
