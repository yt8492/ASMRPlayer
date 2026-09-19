package com.yt8492.asmrplayer.data.model

data class FileExplorerContent(
    val currentPath: String,
    val directoryTitle: String? = null,
    val parentPath: String? = null,
    val directories: List<AudioDirectory>,
    val tracks: List<Track>,
    val images: List<ImageFile>,
)
