package com.yt8492.asmrplayer.data.repository

import com.yt8492.asmrplayer.data.model.Playlist
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>
    fun observeTrackIds(playlistId: Long): Flow<List<Long>>
    suspend fun getPlaylist(playlistId: Long): Playlist?
    suspend fun getTrackIds(playlistId: Long): List<Long>
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(playlistId: Long, name: String)
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun addTrack(playlistId: Long, trackId: Long): AddTrackResult
    suspend fun removeTrack(playlistId: Long, trackId: Long)
    suspend fun replaceTrackOrder(playlistId: Long, trackIds: List<Long>)
}

enum class AddTrackResult {
    Added,
    AlreadyExists,
}
