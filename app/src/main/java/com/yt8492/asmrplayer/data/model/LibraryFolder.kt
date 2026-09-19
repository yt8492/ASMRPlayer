package com.yt8492.asmrplayer.data.model

data class LibraryFolder(
    val uri: String,
    val name: String,
    val audioCount: Int,
    val imageCount: Int,
    val lastScanAt: Long,
    val error: String?,
)
