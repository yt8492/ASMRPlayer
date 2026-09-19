package com.yt8492.asmrplayer.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.yt8492.asmrplayer.data.local.AppDatabase
import com.yt8492.asmrplayer.data.local.LibraryDocumentEntity
import com.yt8492.asmrplayer.data.local.LibraryFolderEntity
import com.yt8492.asmrplayer.data.model.AudioDirectory
import com.yt8492.asmrplayer.data.model.FileExplorerContent
import com.yt8492.asmrplayer.data.model.ImageFile
import com.yt8492.asmrplayer.data.model.LibraryFolder
import com.yt8492.asmrplayer.data.model.Track
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LibraryFolderRepository internal constructor(
    context: Context,
    database: AppDatabase = AppDatabase.getInstance(context),
    private val source: FolderDocumentSource = AndroidFolderDocumentSource(context),
) {
    private val dao = database.libraryFolderDao()

    fun observeFolders() = dao.observeFolders().map { folders -> folders.map { it.toModel() } }.flowOn(Dispatchers.IO)

    suspend fun getFolders(): List<LibraryFolder> = withContext(Dispatchers.IO) {
        dao.getFolders().map { it.toModel() }
    }

    fun hasPermission(uri: String): Boolean = source.hasPermission(uri)

    suspend fun addFolder(uri: Uri) = withContext(Dispatchers.IO) {
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

    suspend fun reloadFolder(uri: String) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val folder = dao.getFolders().firstOrNull { it.uri == uri } ?: return@withLock
            scanFolder(folder)
        }
    }

    suspend fun removeFolder(uri: String) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            // 端末の元ファイルは削除しない。保存済みIDも再追加に備えて残す。
            dao.removeFolder(uri)
            source.releasePermission(uri)
        }
    }

    internal suspend fun rootDirectories(): List<AudioDirectory> = dao.getFolders().map { folder ->
        AudioDirectory(
            path = DocumentPath(folder.uri, DocumentsContract.getTreeDocumentId(Uri.parse(folder.uri))).encode(),
            name = folder.name,
            trackCount = folder.audioCount + folder.imageCount,
        )
    }

    internal suspend fun getContent(path: DocumentPath): FileExplorerContent = withContext(Dispatchers.IO) {
        val folder = dao.getFolders().firstOrNull { it.uri == path.treeUri }
            ?: throw IOException("フォルダは登録解除されています。設定から追加してください。")
        checkPermission(folder.uri)
        val documents = dao.getDocuments(folder.uri).filter { it.active }
        val current = documents.firstOrNull { it.documentId == path.documentId }
            ?: throw IOException("フォルダが見つかりません。設定から再読み込みしてください。")
        val children = documents.filter { it.parentId == path.documentId }.sortedBy { it.name.lowercase() }
        val childCounts = documents.groupingBy { it.parentId }.eachCount()
        FileExplorerContent(
            currentPath = path.encode(),
            directoryTitle = current.name,
            parentPath = current.parentId?.let { DocumentPath(folder.uri, it).encode() } ?: "",
            directories = children.filter { it.mimeType == Document.MIME_TYPE_DIR }.map {
                AudioDirectory(DocumentPath(folder.uri, it.documentId).encode(), it.name, childCounts[it.documentId] ?: 0)
            },
            tracks = children.filter { it.mimeType.startsWith("audio/") }.map { it.toTrack() },
            images = children.filter { it.mimeType.startsWith("image/") }.map {
                ImageFile(it.id, it.name, it.documentUri(), it.mimeType)
            },
        )
    }

    internal suspend fun getTracks(ids: List<Long>): List<Track> = withContext(Dispatchers.IO) {
        ids.filter { it >= DOCUMENT_TRACK_ID_BASE }.map { it - DOCUMENT_TRACK_ID_BASE }.chunked(900).flatMap { chunk ->
            dao.getDocumentsByIds(chunk).filter { source.hasPermission(it.treeUri) && it.mimeType.startsWith("audio/") }
                .map { it.toTrack() }
        }
    }

    private suspend fun scanFolder(folder: LibraryFolderEntity) {
        try {
            checkPermission(folder.uri)
            val tree = Uri.parse(folder.uri)
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val old = dao.getDocuments(folder.uri).associateBy { it.documentId }
            val rootUri = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
            val root = source.queryDocuments(rootUri, folder.uri, null).single()
            val documents = mutableListOf(root)
            val pending = ArrayDeque<String>().apply { add(rootId) }
            val visited = mutableSetOf(rootId)
            while (pending.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val parentId = pending.removeFirst()
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
                source.queryDocuments(childrenUri, folder.uri, parentId).forEach { item ->
                    currentCoroutineContext().ensureActive()
                    if (!visited.add(item.documentId)) return@forEach
                    when {
                        item.mimeType == Document.MIME_TYPE_DIR -> {
                            documents.add(item)
                            pending.add(item.documentId)
                        }
                        item.mimeType.startsWith("audio/") -> {
                            val cached = old[item.documentId]
                            documents.add(if (cached != null && item.modifiedAt > 0 && cached.modifiedAt == item.modifiedAt && cached.size == item.size) {
                                item.copy(title = cached.title, artist = cached.artist, durationMs = cached.durationMs, trackNumber = cached.trackNumber)
                            } else {
                                source.readAudioMetadata(item)
                            })
                        }
                        item.mimeType.startsWith("image/") -> documents.add(item)
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            // 最後まで読み取れた場合だけ一覧を置き換える。途中失敗で既存の曲を消さない。
            dao.replaceSnapshot(
                folder.copy(
                    name = root.name,
                    audioCount = documents.count { it.mimeType.startsWith("audio/") },
                    imageCount = documents.count { it.mimeType.startsWith("image/") },
                    lastScanAt = System.currentTimeMillis(),
                    error = null,
                ),
                documents,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val message = if (error is SecurityException) {
                "アクセスできません。フォルダを追加し直してください。"
            } else {
                "読み込みに失敗しました。保存先の接続を確認して再読み込みしてください。"
            }
            dao.saveFolder(folder.copy(error = message))
            throw IOException(message)
        }
    }

    private fun checkPermission(uri: String) {
        if (!hasPermission(uri)) throw SecurityException("フォルダのアクセス許可がありません")
    }

    private fun LibraryDocumentEntity.toTrack() = Track(
        id = documentTrackId(id), title = title, artist = normalizeArtistName(artist),
        durationMs = durationMs, fileSizeBytes = size, trackNumber = trackNumber, uri = documentUri(),
    )

    private fun LibraryFolderEntity.toModel() = LibraryFolder(
        uri, name, audioCount, imageCount, lastScanAt,
        if (hasPermission(uri)) error else "アクセスできません。フォルダを追加し直してください。",
    )

    companion object {
        private val mutationMutex = Mutex()
    }
}
