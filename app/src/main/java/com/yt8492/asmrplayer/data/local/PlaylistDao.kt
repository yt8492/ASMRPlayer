package com.yt8492.asmrplayer.data.local

import androidx.room.Dao
import androidx.room.Insert
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

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observePlaylistTracks(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getTrackIds(playlistId: Long): List<Long>

    @Query("SELECT id FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistTrackIds(playlistId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getTrackCount(playlistId: Long): Int

    @Insert
    suspend fun insertTrack(track: PlaylistTrackEntity): Long

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND id = :playlistTrackId")
    suspend fun deleteTrack(playlistId: Long, playlistTrackId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearTracks(playlistId: Long)

    @Insert
    suspend fun insertTracksReturningIds(tracks: List<PlaylistTrackEntity>): List<Long>

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND id = :playlistTrackId")
    suspend fun updateTrackPosition(playlistId: Long, playlistTrackId: Long, position: Int)

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun touchPlaylist(playlistId: Long, updatedAt: Long)

    @Transaction
    suspend fun appendTracks(playlistId: Long, trackIds: List<Long>, addedAt: Long): Int {
        if (trackIds.isEmpty()) return 0

        val startPosition = getTrackCount(playlistId)
        val insertedIds = insertTracksReturningIds(
            trackIds.mapIndexed { index, trackId ->
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    trackId = trackId,
                    position = startPosition + index,
                    addedAt = addedAt,
                )
            },
        )
        val addedCount = insertedIds.size
        if (addedCount > 0) {
            touchPlaylist(playlistId, addedAt)
        }
        return addedCount
    }

    @Transaction
    suspend fun replaceTrackOrder(playlistId: Long, playlistTrackIds: List<Long>, updatedAt: Long) {
        playlistTrackIds.forEachIndexed { index, playlistTrackId ->
            updateTrackPosition(playlistId, playlistTrackId, index)
        }
        touchPlaylist(playlistId, updatedAt)
    }

    @Transaction
    suspend fun removeTrackAndReorder(playlistId: Long, playlistTrackId: Long, updatedAt: Long) {
        deleteTrack(playlistId, playlistTrackId)
        replaceTrackOrder(playlistId, getPlaylistTrackIds(playlistId), updatedAt)
    }
}
