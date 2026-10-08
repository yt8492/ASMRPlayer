package com.yt8492.asmrplayer.data.library

import android.net.Uri
import android.provider.DocumentsContract
import com.yt8492.asmrplayer.data.datasource.document.FolderDocument
import com.yt8492.asmrplayer.data.local.entity.LibraryDocumentEntity
import com.yt8492.asmrplayer.data.model.BrowsableDirectory
import com.yt8492.asmrplayer.data.model.DocumentFile
import com.yt8492.asmrplayer.data.model.FileExplorerContent
import com.yt8492.asmrplayer.data.model.ImageFile
import com.yt8492.asmrplayer.data.model.Track
import com.yt8492.asmrplayer.data.model.documentKind

internal fun FolderDocument.toEntity() = LibraryDocumentEntity(
    treeUri = treeUri, documentId = documentId, parentId = parentId, name = name,
    mimeType = mimeType, size = size, modifiedAt = modifiedAt, title = title,
    artist = artist, durationMs = durationMs, trackNumber = trackNumber,
)

internal fun LibraryDocumentEntity.toDocument() = FolderDocument(
    treeUri, documentId, parentId, name, mimeType, size, modifiedAt, title, artist, durationMs, trackNumber,
)

internal fun LibraryDocumentEntity.documentUri(): Uri =
    DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUri), documentId)

internal fun LibraryDocumentEntity.toTrack() = Track(
    id = documentTrackId(id), title = title, artist = normalizeArtistName(artist),
    durationMs = durationMs, fileSizeBytes = size, trackNumber = trackNumber, uri = documentUri(),
)

internal fun directoryContent(path: DocumentPath, documents: List<LibraryDocumentEntity>): FileExplorerContent {
    val current = documents.firstOrNull { it.documentId == path.documentId }
        ?: throw java.io.IOException("フォルダが見つかりません。設定から再読み込みしてください。")
    val children = documents.filter { it.parentId == path.documentId }.sortedBy { it.name.lowercase() }
    val childCounts = documents.groupingBy { it.parentId }.eachCount()
    return FileExplorerContent(
        currentPath = path.encode(), directoryTitle = current.name,
        parentPath = current.parentId?.let { DocumentPath(path.treeUri, it).encode() } ?: "",
        directories = children.filter { it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR }.map {
            BrowsableDirectory(DocumentPath(path.treeUri, it.documentId).encode(), it.name, childCounts[it.documentId] ?: 0)
        },
        tracks = children.filter { it.mimeType.startsWith("audio/") }.map { it.toTrack() },
        documents = children.mapNotNull { item -> documentKind(item.mimeType, item.name)?.let { kind ->
            DocumentFile(item.id, item.name, item.documentUri(), kind, item.size)
        } },
        images = children.filter { it.mimeType.startsWith("image/") }.map {
            ImageFile(it.id, it.name, it.documentUri(), it.mimeType)
        },
    )
}
