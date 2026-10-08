package com.yt8492.asmrplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_folders")
data class LibraryFolderEntity(
    @PrimaryKey val uri: String,
    val name: String,
    val audioCount: Int = 0,
    val imageCount: Int = 0,
    val lastScanAt: Long = 0,
    val error: String? = null,
)
