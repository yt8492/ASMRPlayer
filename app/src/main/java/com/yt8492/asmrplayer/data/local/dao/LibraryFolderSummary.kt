package com.yt8492.asmrplayer.data.local.dao

import androidx.room.Embedded
import com.yt8492.asmrplayer.data.local.entity.LibraryDocumentEntity
import com.yt8492.asmrplayer.data.local.entity.LibraryFolderEntity

data class LibraryFolderSummary(
    @Embedded val folder: LibraryFolderEntity,
    val documentCount: Int,
)

data class LibraryChildCount(val parentId: String, val itemCount: Int)

data class LibraryDirectorySnapshot(
    val current: LibraryDocumentEntity,
    val children: List<LibraryDocumentEntity>,
    val childCounts: Map<String, Int>,
)
