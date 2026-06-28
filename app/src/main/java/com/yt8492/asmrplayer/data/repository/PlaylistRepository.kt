package com.yt8492.asmrplayer.data.repository

import com.yt8492.asmrplayer.data.model.Playlist
import com.yt8492.asmrplayer.data.model.PlaylistTrack
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>
    fun observePlaylistTracks(playlistId: Long): Flow<List<PlaylistTrack>>
    suspend fun getPlaylist(playlistId: Long): Playlist?
    suspend fun getTrackIds(playlistId: Long): List<Long>
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(playlistId: Long, name: String)
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun addTrack(playlistId: Long, trackId: Long): AddTrackResult
    suspend fun addTracks(playlistId: Long, trackIds: List<Long>): AddTracksResult
    suspend fun removeTrack(playlistId: Long, playlistTrackId: Long)
    suspend fun replaceTrackOrder(playlistId: Long, playlistTrackIds: List<Long>)
}

enum class AddTrackResult {
    Added,
}

data class AddTracksResult(
    val addedCount: Int,
    val skippedCount: Int,
)
