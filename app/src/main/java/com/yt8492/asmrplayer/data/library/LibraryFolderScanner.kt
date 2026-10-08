package com.yt8492.asmrplayer.data.library

import android.net.Uri
import android.provider.DocumentsContract
import com.yt8492.asmrplayer.data.datasource.document.FolderDocument
import com.yt8492.asmrplayer.data.datasource.document.FolderDocumentSource
import com.yt8492.asmrplayer.data.model.documentKind
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 走査が完了するまで結果をメモリに保持し、途中失敗時はDBを書き換えない。 */
internal class LibraryFolderScanner(private val source: FolderDocumentSource) {
    suspend fun scan(treeUri: String, old: Map<String, FolderDocument>): List<FolderDocument> {
        val tree = Uri.parse(treeUri)
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val documents = mutableListOf(source.queryDocuments(rootUri, treeUri, null).single())
        val pending = ArrayDeque<String>().apply { add(rootId) }
        val visited = mutableSetOf(rootId)
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val parentId = pending.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            source.queryDocuments(childrenUri, treeUri, parentId).forEach { item ->
                currentCoroutineContext().ensureActive()
                if (!visited.add(item.documentId)) return@forEach
                when {
                    item.mimeType == DocumentsContract.Document.MIME_TYPE_DIR -> {
                        documents.add(item)
                        pending.add(item.documentId)
                    }
                    item.mimeType.startsWith("audio/") -> {
                        val cached = old[item.documentId]
                        documents.add(if (cached != null && item.modifiedAt > 0 && cached.modifiedAt == item.modifiedAt && cached.size == item.size) {
                            item.copy(title = cached.title, artist = cached.artist, durationMs = cached.durationMs, trackNumber = cached.trackNumber)
                        } else source.readAudioMetadata(item))
                    }
                    item.mimeType.startsWith("image/") || documentKind(item.mimeType, item.name) != null -> documents.add(item)
                }
            }
        }
        currentCoroutineContext().ensureActive()
        return documents
    }
}
