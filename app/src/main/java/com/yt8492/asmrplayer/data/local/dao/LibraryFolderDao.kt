package com.yt8492.asmrplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.yt8492.asmrplayer.data.local.entity.LibraryDocumentEntity
import com.yt8492.asmrplayer.data.local.entity.LibraryFolderEntity
import kotlinx.coroutines.flow.Flow

// documentKindのMIME優先・不明なMIMEだけ拡張子で補完する規則と合わせる。
private const val FOLDER_SUMMARY_QUERY = """
    SELECT library_folders.*, COALESCE(previews.documentCount, 0) AS documentCount
    FROM library_folders
    LEFT JOIN (
        SELECT treeUri, COUNT(*) AS documentCount
        FROM library_documents
        WHERE active = 1 AND (
            LOWER(mimeType) IN ('application/pdf', 'text/plain') OR (
                LOWER(mimeType) IN ('', 'application/octet-stream') AND (
                    LOWER(name) GLOB '*.pdf' OR LOWER(name) GLOB '*.txt'
                )
            )
        )
        GROUP BY treeUri
    ) AS previews ON previews.treeUri = library_folders.uri
    ORDER BY library_folders.name COLLATE NOCASE, library_folders.uri
"""

@Dao
abstract class LibraryFolderDao {
    @Query(FOLDER_SUMMARY_QUERY)
    abstract fun observeFolderSummaries(): Flow<List<LibraryFolderSummary>>

    @Query(FOLDER_SUMMARY_QUERY)
    abstract suspend fun getFolderSummaries(): List<LibraryFolderSummary>

    @Query("SELECT * FROM library_folders ORDER BY name COLLATE NOCASE, uri")
    abstract suspend fun getFolders(): List<LibraryFolderEntity>

    @Query("SELECT * FROM library_folders WHERE uri = :uri")
    abstract suspend fun getFolder(uri: String): LibraryFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveFolder(folder: LibraryFolderEntity)

    @Query("DELETE FROM library_folders WHERE uri = :uri")
    abstract suspend fun deleteFolder(uri: String)

    @Query("SELECT * FROM library_documents WHERE treeUri = :uri")
    abstract suspend fun getDocuments(uri: String): List<LibraryDocumentEntity>

    @Query("SELECT * FROM library_documents WHERE treeUri = :uri AND documentId = :documentId AND active = 1")
    abstract suspend fun getActiveDocument(uri: String, documentId: String): LibraryDocumentEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM library_documents WHERE treeUri = :uri AND documentId = :documentId AND active = 1)")
    abstract suspend fun hasActiveDocument(uri: String, documentId: String): Boolean

    @Query("SELECT * FROM library_documents WHERE treeUri = :uri AND parentId = :parentId AND active = 1")
    abstract suspend fun getChildren(uri: String, parentId: String): List<LibraryDocumentEntity>

    @Query("SELECT * FROM library_documents WHERE treeUri = :uri AND parentId = :parentId AND active = 1 AND mimeType GLOB 'audio/*'")
    abstract suspend fun getAudioChildren(uri: String, parentId: String): List<LibraryDocumentEntity>

    @Query("""
        SELECT parentId, COUNT(*) AS itemCount FROM library_documents
        WHERE treeUri = :uri AND active = 1 AND parentId IN (
            SELECT documentId FROM library_documents
            WHERE treeUri = :uri AND parentId = :parentId AND active = 1
                AND mimeType = 'vnd.android.document/directory'
        )
        GROUP BY parentId
    """)
    abstract suspend fun getChildCounts(uri: String, parentId: String): List<LibraryChildCount>

    @Query("SELECT * FROM library_documents WHERE id IN (:ids) AND active = 1 AND mimeType GLOB 'audio/*' AND treeUri IN (SELECT uri FROM library_folders)")
    abstract suspend fun getDocumentsByIds(ids: List<Long>): List<LibraryDocumentEntity>

    @Transaction
    open suspend fun getDirectorySnapshot(uri: String, documentId: String): LibraryDirectorySnapshot? {
        val current = getActiveDocument(uri, documentId) ?: return null
        val children = getChildren(uri, documentId)
        val counts = if (children.any { it.mimeType == "vnd.android.document/directory" }) {
            getChildCounts(uri, documentId).associate { it.parentId to it.itemCount }
        } else emptyMap()
        return LibraryDirectorySnapshot(current, children, counts)
    }

    @Transaction
    open suspend fun getDirectoryTracks(uri: String, documentId: String): List<LibraryDocumentEntity>? {
        if (!hasActiveDocument(uri, documentId)) return null
        return getAudioChildren(uri, documentId)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveDocuments(documents: List<LibraryDocumentEntity>)

    @Query("UPDATE library_documents SET active = 0 WHERE treeUri = :uri")
    abstract suspend fun deactivateDocuments(uri: String)

    // 消えたファイルもIDを保持し、再追加時にプレイリストやリピート設定を復元する。
    @Transaction
    open suspend fun replaceSnapshot(folder: LibraryFolderEntity, documents: List<LibraryDocumentEntity>) {
        val oldIds = getDocuments(folder.uri).associate { it.documentId to it.id }
        deactivateDocuments(folder.uri)
        saveDocuments(documents.map { it.copy(id = oldIds[it.documentId] ?: 0) })
        saveFolder(folder)
    }

    @Transaction
    open suspend fun removeFolder(uri: String) {
        deactivateDocuments(uri)
        deleteFolder(uri)
    }
}
