package com.yt8492.asmrplayer.data.repository.impl

import android.net.Uri
import android.provider.DocumentsContract
import com.yt8492.asmrplayer.data.datasource.document.FolderDocumentSource
import com.yt8492.asmrplayer.data.library.DOCUMENT_TRACK_ID_BASE
import com.yt8492.asmrplayer.data.library.DocumentPath
import com.yt8492.asmrplayer.data.library.LibraryFolderScanner
import com.yt8492.asmrplayer.data.library.directoryContent
import com.yt8492.asmrplayer.data.library.toDocument
import com.yt8492.asmrplayer.data.library.toEntity
import com.yt8492.asmrplayer.data.library.toTrack
import com.yt8492.asmrplayer.data.local.dao.LibraryFolderDao
import com.yt8492.asmrplayer.data.local.entity.LibraryFolderEntity
import com.yt8492.asmrplayer.data.model.BrowsableDirectory
import com.yt8492.asmrplayer.data.model.FileExplorerContent
import com.yt8492.asmrplayer.data.model.LibraryFolder
import com.yt8492.asmrplayer.data.model.Track
import com.yt8492.asmrplayer.data.model.documentKind
import com.yt8492.asmrplayer.data.repository.DifferentFolderSelectedException
import com.yt8492.asmrplayer.data.repository.LibraryFolderRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class LibraryFolderRepositoryImpl(
    private val dao: LibraryFolderDao,
    private val source: FolderDocumentSource,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) : LibraryFolderRepository {
    private val scanner = LibraryFolderScanner(source)

    override fun observeFolders() = combine(dao.observeFolders(), dao.observeActiveDocuments()) { folders, documents ->
        val counts = documents.filter { documentKind(it.mimeType, it.name) != null }.groupingBy { it.treeUri }.eachCount()
        folders.map { it.toModel(counts[it.uri] ?: 0) }
    }.flowOn(ioDispatcher)

    override suspend fun getFolders(): List<LibraryFolder> = withContext(ioDispatcher) {
        dao.getFolders().map { it.toModel(documentCount(it.uri)) }
    }

    override fun hasPermission(uri: String): Boolean = source.hasPermission(uri)

    override suspend fun addFolder(uri: Uri) = withContext(ioDispatcher) {
        mutationMutex.withLock {
            source.persistPermission(uri)
            val existing = dao.getFolders().firstOrNull { it.uri == uri.toString() }
            val folder = existing ?: LibraryFolderEntity(
                uri = uri.toString(),
                name = DocumentsContract.getTreeDocumentId(uri).substringAfterLast('/').substringAfterLast(':'),
            )
            dao.saveFolder(folder)
            scanFolder(folder)
        }
    }

    override suspend fun reloadFolder(uri: String) = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val folder = dao.getFolders().firstOrNull { it.uri == uri } ?: return@withLock
            scanFolder(folder)
        }
    }

    override suspend fun restoreFolderAccess(folderUri: String, selectedUri: Uri) = withContext(ioDispatcher) {
        mutationMutex.withLock {
            if (folderUri != selectedUri.toString()) throw DifferentFolderSelectedException()
            val folder = dao.getFolders().firstOrNull { it.uri == folderUri }
                ?: throw IOException("フォルダは登録解除されています。")
            source.persistPermission(selectedUri)
            // 同じ登録と文書IDを使い、プレイリストなどの参照を維持する。
            scanFolder(folder)
        }
    }

    override suspend fun removeFolder(uri: String) = withContext(ioDispatcher) {
        mutationMutex.withLock {
            // 端末の元ファイルは削除しない。保存済みIDも再追加に備えて残す。
            dao.removeFolder(uri)
            source.releasePermission(uri)
        }
    }

    override suspend fun rootDirectories(): List<BrowsableDirectory> = withContext(ioDispatcher) {
        dao.getFolders().map { folder ->
            BrowsableDirectory(
                path = DocumentPath(folder.uri, DocumentsContract.getTreeDocumentId(Uri.parse(folder.uri))).encode(),
                name = folder.name,
                itemCount = folder.audioCount + folder.imageCount + documentCount(folder.uri),
                hasPermission = hasPermission(folder.uri),
            )
        }
    }

    override suspend fun getContent(directoryPath: String): FileExplorerContent = withContext(ioDispatcher) {
        val path = DocumentPath.parse(directoryPath) ?: throw IOException("フォルダの参照先が不正です。")
        val folder = dao.getFolders().firstOrNull { it.uri == path.treeUri }
            ?: throw IOException("フォルダは登録解除されています。設定から追加してください。")
        checkPermission(folder.uri)
        directoryContent(path, dao.getDocuments(folder.uri).filter { it.active })
    }

    override suspend fun getTracks(ids: List<Long>): List<Track> = withContext(ioDispatcher) {
        ids.filter { it >= DOCUMENT_TRACK_ID_BASE }.map { it - DOCUMENT_TRACK_ID_BASE }.chunked(900).flatMap { chunk ->
            dao.getDocumentsByIds(chunk).filter { source.hasPermission(it.treeUri) && it.mimeType.startsWith("audio/") }
                .map { it.toTrack() }
        }
    }

    private suspend fun scanFolder(folder: LibraryFolderEntity) {
        // 再インストール後などの再許可待ちは、読み込み失敗として保存しない。
        checkPermission(folder.uri)
        try {
            val old = dao.getDocuments(folder.uri).associate { it.documentId to it.toDocument() }
            val documents = scanner.scan(folder.uri, old)
            // 最後まで読み取れた場合だけ一覧を置き換える。途中失敗で既存の曲を消さない。
            dao.replaceSnapshot(
                folder.copy(
                    name = documents.first().name,
                    audioCount = documents.count { it.mimeType.startsWith("audio/") },
                    imageCount = documents.count { it.mimeType.startsWith("image/") },
                    lastScanAt = now(),
                    error = null,
                ),
                documents.map { it.toEntity() },
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is SecurityException && !hasPermission(folder.uri)) throw error
            val message = "読み込みに失敗しました。保存先の接続を確認して再読み込みしてください。"
            dao.saveFolder(folder.copy(error = message))
            throw IOException(message)
        }
    }

    private fun checkPermission(uri: String) {
        if (!hasPermission(uri)) throw SecurityException("フォルダのアクセス許可がありません")
    }

    private suspend fun documentCount(uri: String): Int = dao.getDocuments(uri).count {
        it.active && documentKind(it.mimeType, it.name) != null
    }

    private fun LibraryFolderEntity.toModel(documentCount: Int): LibraryFolder {
        val granted = hasPermission(uri)
        return LibraryFolder(
            uri, name, audioCount, imageCount, lastScanAt,
            error = if (granted) error else null,
            documentCount = documentCount,
            hasPermission = granted,
        )
    }

    companion object {
        private val mutationMutex = Mutex()
    }
}
