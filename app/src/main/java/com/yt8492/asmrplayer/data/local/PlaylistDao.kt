package com.yt8492.asmrplayer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT playlists.id, playlists.name, playlists.createdAt, playlists.updatedAt,
            COUNT(playlist_tracks.trackId) AS trackCount
        FROM playlists
        LEFT JOIN playlist_tracks ON playlists.id = playlist_tracks.playlistId
        GROUP BY playlists.id
        ORDER BY playlists.updatedAt DESC
        """,
    )
    fun observePlaylists(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylist(playlistId: Long): PlaylistEntity?

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeTrackIds(playlistId: Long): Flow<List<Long>>

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getTrackIds(playlistId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getTrackCount(playlistId: Long): Int

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun countTrack(playlistId: Long, trackId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(track: PlaylistTrackEntity): Long

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deleteTrack(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearTracks(playlistId: Long)

    @Insert
    suspend fun insertTracks(tracks: List<PlaylistTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracksIgnoringConflicts(tracks: List<PlaylistTrackEntity>): List<Long>

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun touchPlaylist(playlistId: Long, updatedAt: Long)

    @Transaction
    suspend fun appendTracks(playlistId: Long, trackIds: List<Long>, addedAt: Long): Int {
        val currentTrackIds = getTrackIds(playlistId).toSet()
        val tracksToAdd = trackIds
            .distinct()
            .filterNot { it in currentTrackIds }
        if (tracksToAdd.isEmpty()) return 0

        val startPosition = getTrackCount(playlistId)
        val insertedIds = insertTracksIgnoringConflicts(
            tracksToAdd.mapIndexed { index, trackId ->
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    trackId = trackId,
                    position = startPosition + index,
                    addedAt = addedAt,
                )
            },
        )
        val addedCount = insertedIds.count { it != -1L }
        if (addedCount > 0) {
            touchPlaylist(playlistId, addedAt)
        }
        return addedCount
    }

    @Transaction
    suspend fun replaceTrackOrder(playlistId: Long, trackIds: List<Long>, updatedAt: Long) {
        clearTracks(playlistId)
        insertTracks(
            trackIds.mapIndexed { index, trackId ->
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    trackId = trackId,
                    position = index,
                    addedAt = updatedAt,
                )
            },
        )
        touchPlaylist(playlistId, updatedAt)
    }
}
