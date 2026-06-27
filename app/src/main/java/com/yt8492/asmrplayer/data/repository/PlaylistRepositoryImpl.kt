package com.yt8492.asmrplayer.data.repository

import com.yt8492.asmrplayer.data.local.PlaylistDao
import com.yt8492.asmrplayer.data.local.PlaylistEntity
import com.yt8492.asmrplayer.data.local.PlaylistSummary
import com.yt8492.asmrplayer.data.local.PlaylistTrackEntity
import com.yt8492.asmrplayer.data.model.Playlist
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

    override fun observeTrackIds(playlistId: Long): Flow<List<Long>> {
        return playlistDao.observeTrackIds(playlistId)
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
        if (playlistDao.countTrack(playlistId, trackId) > 0) {
            return@withContext AddTrackResult.AlreadyExists
        }
        val now = System.currentTimeMillis()
        val position = playlistDao.getTrackCount(playlistId)
        val inserted = playlistDao.insertTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = trackId,
                position = position,
                addedAt = now,
            ),
        )
        playlistDao.touchPlaylist(playlistId, now)
        if (inserted == -1L) AddTrackResult.AlreadyExists else AddTrackResult.Added
    }

    override suspend fun addTracks(playlistId: Long, trackIds: List<Long>): AddTracksResult = withContext(Dispatchers.IO) {
        val uniqueTrackIds = trackIds.distinct()
        if (uniqueTrackIds.isEmpty()) {
            return@withContext AddTracksResult(addedCount = 0, skippedCount = 0)
        }
        val addedCount = playlistDao.appendTracks(
            playlistId = playlistId,
            trackIds = uniqueTrackIds,
            addedAt = System.currentTimeMillis(),
        )
        AddTracksResult(
            addedCount = addedCount,
            skippedCount = uniqueTrackIds.size - addedCount,
        )
    }

    override suspend fun removeTrack(playlistId: Long, trackId: Long) = withContext(Dispatchers.IO) {
        val remaining = PlaylistTrackOrder.remove(playlistDao.getTrackIds(playlistId), trackId)
        playlistDao.replaceTrackOrder(playlistId, remaining, System.currentTimeMillis())
    }

    override suspend fun replaceTrackOrder(playlistId: Long, trackIds: List<Long>) = withContext(Dispatchers.IO) {
        playlistDao.replaceTrackOrder(playlistId, trackIds, System.currentTimeMillis())
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
}
