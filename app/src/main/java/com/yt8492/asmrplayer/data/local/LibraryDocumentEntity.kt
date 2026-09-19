package com.yt8492.asmrplayer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "library_documents",
    indices = [Index(value = ["treeUri", "documentId"], unique = true)],
)
data class LibraryDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treeUri: String,
    val documentId: String,
    val parentId: String?,
    val name: String,
    val mimeType: String,
    val size: Long?,
    val modifiedAt: Long,
    val title: String = name,
    val artist: String = "",
    val durationMs: Long = 0,
    val trackNumber: Int = 0,
    val active: Boolean = true,
)
