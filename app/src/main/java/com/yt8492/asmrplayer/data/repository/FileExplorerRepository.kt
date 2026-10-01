package com.yt8492.asmrplayer.data.repository

import com.yt8492.asmrplayer.data.model.FileExplorerContent

interface FileExplorerRepository {
    suspend fun getContent(directoryPath: String): FileExplorerContent

    /** 選択済みフォルダの内容を再読み込みする。 */
    suspend fun scanDirectory(directoryPath: String): Boolean
}
