package com.yt8492.asmrplayer.data.repository

import com.yt8492.asmrplayer.data.model.FileExplorerContent

interface FileExplorerRepository {
    suspend fun getContent(directoryPath: String): FileExplorerContent
}
