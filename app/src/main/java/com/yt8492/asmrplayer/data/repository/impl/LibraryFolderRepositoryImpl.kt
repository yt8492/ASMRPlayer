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
import com.yt8492.asmrplayer.data.repository.DifferentFolderSelectedException
import com.yt8492.asmrplayer.data.repository.LibraryFolderRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
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

    override fun observeFolders() = dao.observeFolderSummaries().map { summaries ->
        val granted = source.readableTreeUris()
        summaries.map { it.folder.toModel(it.documentCount, granted) }
    }.flowOn(ioDispatcher)

    override suspend fun getFolders(): List<LibraryFolder> = withContext(ioDispatcher) {
        val summaries = dao.getFolderSummaries()
        val granted = source.readableTreeUris()
        summaries.map { it.folder.toModel(it.documentCount, granted) }
    }

    override fun hasPermission(uri: String): Boolean = source.hasPermission(uri)

    override suspend fun addFolder(uri: Uri) = withContext(ioDispatcher) {
        mutationMutex.withLock {
            source.persistPermission(uri)
            val existing = dao.getFolder(uri.toString())
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
            val folder = dao.getFolder(uri) ?: return@withLock
            scanFolder(folder)
        }
    }

    override suspend fun restoreFolderAccess(folderUri: String, selectedUri: Uri) = withContext(ioDispatcher) {
        mutationMutex.withLock {
            if (folderUri != selectedUri.toString()) throw DifferentFolderSelectedException()
            val folder = dao.getFolder(folderUri)
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
        val summaries = dao.getFolderSummaries()
        val granted = source.readableTreeUris()
        summaries.map { (folder, documentCount) ->
            BrowsableDirectory(
                path = DocumentPath(folder.uri, DocumentsContract.getTreeDocumentId(Uri.parse(folder.uri))).encode(),
                name = folder.name,
                itemCount = folder.audioCount + folder.imageCount + documentCount,
                hasPermission = folder.uri in granted,
            )
        }
    }

    override suspend fun getContent(directoryPath: String): FileExplorerContent = withContext(ioDispatcher) {
        val path = registeredPath(directoryPath)
        val snapshot = dao.getDirectorySnapshot(path.treeUri, path.documentId)
            ?: throw IOException("フォルダが見つかりません。設定から再読み込みしてください。")
        directoryContent(path, snapshot)
    }

    override suspend fun getTracksInDirectory(directoryPath: String): List<Track> = withContext(ioDispatcher) {
        val path = registeredPath(directoryPath)
        val tracks = dao.getDirectoryTracks(path.treeUri, path.documentId)
            ?: throw IOException("フォルダが見つかりません。設定から再読み込みしてください。")
        tracks.sortedBy { it.name.lowercase() }.map { it.toTrack() }
    }

    override suspend fun getTracks(ids: List<Long>): List<Track> = withContext(ioDispatcher) {
        val documentIds = ids.asSequence().filter { it >= DOCUMENT_TRACK_ID_BASE }
            .map { it - DOCUMENT_TRACK_ID_BASE }.distinct().toList()
        if (documentIds.isEmpty()) return@withContext emptyList()
        val granted = source.readableTreeUris()
        documentIds.chunked(900).flatMap { chunk ->
            dao.getDocumentsByIds(chunk).filter { it.treeUri in granted }
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

    private suspend fun registeredPath(directoryPath: String): DocumentPath {
        val path = DocumentPath.parse(directoryPath) ?: throw IOException("フォルダの参照先が不正です。")
        if (dao.getFolder(path.treeUri) == null) {
            throw IOException("フォルダは登録解除されています。設定から追加してください。")
        }
        checkPermission(path.treeUri)
        return path
    }

    private fun LibraryFolderEntity.toModel(documentCount: Int, readableTreeUris: Set<String>): LibraryFolder {
        val granted = uri in readableTreeUris
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
