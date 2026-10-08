package com.yt8492.asmrplayer.data.datasource.document

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.webkit.MimeTypeMap
import com.yt8492.asmrplayer.data.library.normalizeArtistName
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class AndroidFolderDocumentSource(private val context: Context) : FolderDocumentSource {
    private val resolver = context.contentResolver

    override fun hasPermission(uri: String): Boolean = resolver.persistedUriPermissions.any {
        it.uri.toString() == uri && it.isReadPermission
    }

    override fun persistPermission(uri: Uri) {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    override fun releasePermission(uri: String) {
        try {
            resolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // OS側ですでに解除されている場合も登録の解除を完了できる。
        }
    }

    override suspend fun queryDocuments(uri: Uri, treeUri: String, parentId: String?): List<FolderDocument> {
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
        val cursor = resolver.query(uri, projection, null, null, null) ?: throw IOException("フォルダを読み取れません")
        return cursor.use {
            if (it.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false)) {
                throw IOException("保存先がファイル一覧を準備しています")
            }
            buildList {
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val name = it.getString(1).orEmpty()
                    val mime = it.getString(2).orEmpty().let { type ->
                        if (type.isEmpty() || type == "application/octet-stream") {
                            MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: type
                        } else type
                    }
                    add(FolderDocument(
                        treeUri = treeUri, documentId = it.getString(0), parentId = parentId,
                        name = name, mimeType = mime, size = if (it.isNull(3)) null else it.getLong(3),
                        modifiedAt = if (it.isNull(4)) 0 else it.getLong(4),
                    ))
                }
            }
        }
    }

    override fun readAudioMetadata(item: FolderDocument): FolderDocument {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, item.documentUri())
            item.copy(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: item.name,
                artist = normalizeArtistName(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.toIntOrNull() ?: 0,
            )
        } catch (_: Exception) {
            item
        } finally {
            retriever.release()
        }
    }

}

internal fun FolderDocument.documentUri(): Uri =
    DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUri), documentId)
