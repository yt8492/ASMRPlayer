package com.yt8492.asmrplayer.ui.fileexplorer

import com.yt8492.asmrplayer.data.model.AudioDirectory
import com.yt8492.asmrplayer.data.model.Track

data class FileExplorerUiState(
    val isLoading: Boolean = false,
    val currentPath: String = "",
    val directories: List<AudioDirectory> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val errorMessage: String? = null,
)
