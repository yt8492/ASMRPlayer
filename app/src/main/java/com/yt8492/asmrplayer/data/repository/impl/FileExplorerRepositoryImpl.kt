package com.yt8492.asmrplayer.data.repository.impl

import com.yt8492.asmrplayer.data.library.DocumentPath
import com.yt8492.asmrplayer.data.model.FileExplorerContent
import com.yt8492.asmrplayer.data.repository.FileExplorerRepository
import com.yt8492.asmrplayer.data.repository.LibraryFolderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class FileExplorerRepositoryImpl(
    private val folderRepository: LibraryFolderRepository,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) : FileExplorerRepository {

    override suspend fun getContent(directoryPath: String): FileExplorerContent = withContext(ioDispatcher) {
        DocumentPath.parse(directoryPath)?.let {
            // 権限が失われたフォルダを開いていた場合は、再許可を案内する一覧へ戻す。
            if (folderRepository.hasPermission(it.treeUri)) return@withContext folderRepository.getContent(it.encode())
        }
        FileExplorerContent(
            currentPath = "",
            directories = folderRepository.rootDirectories(),
            tracks = emptyList(),
            images = emptyList(),
        )
    }

    override suspend fun scanDirectory(directoryPath: String): Boolean = withContext(ioDispatcher) {
        val path = DocumentPath.parse(directoryPath) ?: return@withContext false
        if (!folderRepository.hasPermission(path.treeUri)) return@withContext false
        folderRepository.reloadFolder(path.treeUri)
        true
    }
}
