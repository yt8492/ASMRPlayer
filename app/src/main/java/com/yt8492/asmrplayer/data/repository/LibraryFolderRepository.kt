package com.yt8492.asmrplayer.data.repository

import android.net.Uri
import com.yt8492.asmrplayer.data.model.BrowsableDirectory
import com.yt8492.asmrplayer.data.model.FileExplorerContent
import com.yt8492.asmrplayer.data.model.LibraryFolder
import com.yt8492.asmrplayer.data.model.Track
import kotlinx.coroutines.flow.Flow

interface LibraryFolderRepository {
    fun observeFolders(): Flow<List<LibraryFolder>>
    suspend fun getFolders(): List<LibraryFolder>
    fun hasPermission(uri: String): Boolean
    suspend fun addFolder(uri: Uri)
    suspend fun reloadFolder(uri: String)
    suspend fun restoreFolderAccess(folderUri: String, selectedUri: Uri)
    suspend fun removeFolder(uri: String)
    suspend fun rootDirectories(): List<BrowsableDirectory>
    suspend fun getContent(directoryPath: String): FileExplorerContent
    suspend fun getTracksInDirectory(directoryPath: String): List<Track>
    suspend fun getTracks(ids: List<Long>): List<Track>
}

class DifferentFolderSelectedException : Exception()
