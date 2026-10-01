package com.yt8492.asmrplayer.data.repository

import android.content.Context
import com.yt8492.asmrplayer.data.model.FileExplorerContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileExplorerRepositoryImpl internal constructor(
    context: Context,
    private val folderRepository: LibraryFolderRepository = LibraryFolderRepository(context),
) : FileExplorerRepository {
    override suspend fun getContent(directoryPath: String): FileExplorerContent = withContext(Dispatchers.IO) {
        DocumentPath.parse(directoryPath)?.let {
            // 権限が失われたフォルダを開いていた場合は、再許可を案内する一覧へ戻す。
            if (folderRepository.hasPermission(it.treeUri)) return@withContext folderRepository.getContent(it)
        }
        FileExplorerContent(
            currentPath = "",
            directories = folderRepository.rootDirectories(),
            tracks = emptyList(),
            images = emptyList(),
        )
    }

    override suspend fun scanDirectory(directoryPath: String): Boolean = withContext(Dispatchers.IO) {
        val path = DocumentPath.parse(directoryPath) ?: return@withContext false
        if (!folderRepository.hasPermission(path.treeUri)) return@withContext false
        folderRepository.reloadFolder(path.treeUri)
        true
    }
}

fun normalizeDirectoryPath(path: String): String {
    val trimmedPath = path.trim().trim('/')
    return if (trimmedPath.isEmpty()) {
        ""
    } else {
        "$trimmedPath/"
    }
}
