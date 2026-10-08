package com.yt8492.asmrplayer.data.repository.impl

import com.yt8492.asmrplayer.data.local.dao.PlaylistDao
import com.yt8492.asmrplayer.data.local.dao.PlaylistSummary
import com.yt8492.asmrplayer.data.local.entity.PlaylistEntity
import com.yt8492.asmrplayer.data.local.entity.PlaylistTrackEntity
import com.yt8492.asmrplayer.data.model.Playlist
import com.yt8492.asmrplayer.data.model.PlaylistTrack
import com.yt8492.asmrplayer.data.repository.AddTrackResult
import com.yt8492.asmrplayer.data.repository.AddTracksResult
import com.yt8492.asmrplayer.data.repository.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class PlaylistRepositoryImpl(
    private val playlistDao: PlaylistDao,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
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

    override suspend fun getPlaylist(playlistId: Long): Playlist? = withContext(ioDispatcher) {
        val entity = playlistDao.getPlaylist(playlistId) ?: return@withContext null
        Playlist(
            id = entity.id,
            name = entity.name,
            trackCount = playlistDao.getTrackCount(playlistId),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }

    override suspend fun getPlaylistTracks(playlistId: Long): List<PlaylistTrack> = withContext(ioDispatcher) {
        playlistDao.getPlaylistTracks(playlistId).map { it.toModel() }
    }

    override suspend fun getTrackIds(playlistId: Long): List<Long> = withContext(ioDispatcher) {
        playlistDao.getTrackIds(playlistId)
    }

    override suspend fun createPlaylist(name: String): Long = withContext(ioDispatcher) {
        val now = now()
        playlistDao.insertPlaylist(
            PlaylistEntity(
                name = name.trim(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun renamePlaylist(playlistId: Long, name: String) = withContext(ioDispatcher) {
        val playlist = playlistDao.getPlaylist(playlistId) ?: return@withContext
        playlistDao.updatePlaylist(
            playlist.copy(
                name = name.trim(),
                updatedAt = now(),
            ),
        )
    }

    override suspend fun deletePlaylist(playlistId: Long) = withContext(ioDispatcher) {
        playlistDao.deletePlaylist(playlistId)
    }

    override suspend fun addTrack(playlistId: Long, trackId: Long): AddTrackResult = withContext(ioDispatcher) {
        playlistDao.appendTracks(playlistId, listOf(trackId), now())
        AddTrackResult.Added
    }

    override suspend fun addTracks(playlistId: Long, trackIds: List<Long>): AddTracksResult = withContext(ioDispatcher) {
        if (trackIds.isEmpty()) {
            return@withContext AddTracksResult(addedCount = 0, skippedCount = 0)
        }
        val addedCount = playlistDao.appendTracks(
            playlistId = playlistId,
            trackIds = trackIds,
            addedAt = now(),
        )
        AddTracksResult(
            addedCount = addedCount,
            skippedCount = trackIds.size - addedCount,
        )
    }

    override suspend fun removeTrack(playlistId: Long, playlistTrackId: Long) = withContext(ioDispatcher) {
        playlistDao.removeTrackAndReorder(playlistId, playlistTrackId, now())
    }

    override suspend fun replaceTrackOrder(playlistId: Long, playlistTrackIds: List<Long>) = withContext(ioDispatcher) {
        playlistDao.replaceTrackOrder(playlistId, playlistTrackIds, now())
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
