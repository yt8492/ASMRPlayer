package com.yt8492.asmrplayer.data.model

data class FileExplorerContent(
    val currentPath: String,
    val directoryTitle: String? = null,
    val parentPath: String? = null,
    val directories: List<BrowsableDirectory>,
    val tracks: List<Track>,
    val documents: List<DocumentFile> = emptyList(),
    val images: List<ImageFile>,
)
