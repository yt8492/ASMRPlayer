package com.yt8492.asmrplayer.data.repository.impl

import com.yt8492.asmrplayer.data.library.DocumentPath
import com.yt8492.asmrplayer.data.model.Track
import com.yt8492.asmrplayer.data.repository.LibraryFolderRepository
import com.yt8492.asmrplayer.data.repository.TrackRepository

internal class TrackRepositoryImpl(
    private val folderRepository: LibraryFolderRepository,
) : TrackRepository {
    override suspend fun getTracks(trackIds: List<Long>): List<Track> {
        val tracksById = folderRepository.getTracks(trackIds).associateBy { it.id }
        return trackIds.mapNotNull { tracksById[it] }
    }

    override suspend fun getTracksInDirectory(directoryPath: String): List<Track> {
        val path = DocumentPath.parse(directoryPath) ?: return emptyList()
        return folderRepository.getTracksInDirectory(path.encode())
    }
}
