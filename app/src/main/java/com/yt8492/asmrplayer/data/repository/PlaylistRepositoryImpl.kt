package com.yt8492.asmrplayer.data.repository

import com.yt8492.asmrplayer.data.local.PlaylistDao
import com.yt8492.asmrplayer.data.local.PlaylistEntity
import com.yt8492.asmrplayer.data.local.PlaylistSummary
import com.yt8492.asmrplayer.data.local.PlaylistTrackEntity
import com.yt8492.asmrplayer.data.model.Playlist
import com.yt8492.asmrplayer.data.model.PlaylistTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PlaylistRepositoryImpl(
    private val playlistDao: PlaylistDao,
) : PlaylistRepository {
    override fun observePlaylists(): Flow<List<Playlist>> {
        return playlistDao.observePlaylists().map { playlists ->
            playlists.map { it.toModel() }
        }
    }

    override fun observePlaylistTracks(playlistId: Long): Flow<List<PlaylistTrack>> {
        return playlistDao.observePlaylistTracks(playlistId).map { playlistTracks ->
            playlistTracks.map { it.toModel() }
        }
    }

    override suspend fun getPlaylist(playlistId: Long): Playlist? = withContext(Dispatchers.IO) {
        val entity = playlistDao.getPlaylist(playlistId) ?: return@withContext null
        Playlist(
            id = entity.id,
            name = entity.name,
            trackCount = playlistDao.getTrackCount(playlistId),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }

    override suspend fun getTrackIds(playlistId: Long): List<Long> = withContext(Dispatchers.IO) {
        playlistDao.getTrackIds(playlistId)
    }

    override suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        playlistDao.insertPlaylist(
            PlaylistEntity(
                name = name.trim(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun renamePlaylist(playlistId: Long, name: String) = withContext(Dispatchers.IO) {
        val playlist = playlistDao.getPlaylist(playlistId) ?: return@withContext
        playlistDao.updatePlaylist(
            playlist.copy(
                name = name.trim(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlistId)
    }

    override suspend fun addTrack(playlistId: Long, trackId: Long): AddTrackResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val position = playlistDao.getTrackCount(playlistId)
        playlistDao.insertTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = trackId,
                position = position,
                addedAt = now,
            ),
        )
        playlistDao.touchPlaylist(playlistId, now)
        AddTrackResult.Added
    }

    override suspend fun addTracks(playlistId: Long, trackIds: List<Long>): AddTracksResult = withContext(Dispatchers.IO) {
        if (trackIds.isEmpty()) {
            return@withContext AddTracksResult(addedCount = 0, skippedCount = 0)
        }
        val addedCount = playlistDao.appendTracks(
            playlistId = playlistId,
            trackIds = trackIds,
            addedAt = System.currentTimeMillis(),
        )
        AddTracksResult(
            addedCount = addedCount,
            skippedCount = trackIds.size - addedCount,
        )
    }

    override suspend fun removeTrack(playlistId: Long, playlistTrackId: Long) = withContext(Dispatchers.IO) {
        playlistDao.removeTrackAndReorder(playlistId, playlistTrackId, System.currentTimeMillis())
    }

    override suspend fun replaceTrackOrder(playlistId: Long, playlistTrackIds: List<Long>) = withContext(Dispatchers.IO) {
        playlistDao.replaceTrackOrder(playlistId, playlistTrackIds, System.currentTimeMillis())
    }

    private fun PlaylistSummary.toModel(): Playlist {
        return Playlist(
            id = id,
            name = name,
            trackCount = trackCount,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun PlaylistTrackEntity.toModel(): PlaylistTrack {
        return PlaylistTrack(
            id = id,
            trackId = trackId,
        )
    }
}
