package com.yt8492.asmrplayer.ui.fileexplorer

import com.yt8492.asmrplayer.data.model.AudioDirectory
import com.yt8492.asmrplayer.data.model.ImageFile
import com.yt8492.asmrplayer.data.model.Playlist
import com.yt8492.asmrplayer.data.model.Track

data class FileExplorerUiState(
    val isLoading: Boolean = false,
    val currentPath: String = "",
    val directories: List<AudioDirectory> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val images: List<ImageFile> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val errorMessage: String? = null,
    val playlistMessage: String? = null,
)
