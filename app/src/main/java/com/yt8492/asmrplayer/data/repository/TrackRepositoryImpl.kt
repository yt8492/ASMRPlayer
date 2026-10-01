package com.yt8492.asmrplayer.data.repository

import android.content.Context
import com.yt8492.asmrplayer.data.model.Track

class TrackRepositoryImpl internal constructor(
    context: Context,
    private val folderRepository: LibraryFolderRepository = LibraryFolderRepository(context),
) : TrackRepository {
    override suspend fun getTracks(trackIds: List<Long>): List<Track> {
        val tracksById = folderRepository.getTracks(trackIds).associateBy { it.id }
        return trackIds.mapNotNull { tracksById[it] }
    }

    override suspend fun getTracksInDirectory(directoryPath: String): List<Track> {
        val path = DocumentPath.parse(directoryPath) ?: return emptyList()
        return folderRepository.getContent(path).tracks
    }
}
