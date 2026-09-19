package com.yt8492.asmrplayer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LibraryFolderDao {
    @Query("SELECT * FROM library_folders ORDER BY name COLLATE NOCASE, uri")
    abstract fun observeFolders(): Flow<List<LibraryFolderEntity>>

    @Query("SELECT * FROM library_folders ORDER BY name COLLATE NOCASE, uri")
    abstract suspend fun getFolders(): List<LibraryFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveFolder(folder: LibraryFolderEntity)

    @Query("DELETE FROM library_folders WHERE uri = :uri")
    abstract suspend fun deleteFolder(uri: String)

    @Query("SELECT * FROM library_documents WHERE treeUri = :uri")
    abstract suspend fun getDocuments(uri: String): List<LibraryDocumentEntity>

    @Query("SELECT * FROM library_documents WHERE id IN (:ids) AND active = 1 AND treeUri IN (SELECT uri FROM library_folders)")
    abstract suspend fun getDocumentsByIds(ids: List<Long>): List<LibraryDocumentEntity>

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
