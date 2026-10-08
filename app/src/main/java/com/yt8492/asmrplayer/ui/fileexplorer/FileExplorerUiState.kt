package com.yt8492.asmrplayer.ui.fileexplorer

import com.yt8492.asmrplayer.data.model.BrowsableDirectory
import com.yt8492.asmrplayer.data.model.DocumentFile
import com.yt8492.asmrplayer.data.model.ImageFile
import com.yt8492.asmrplayer.data.model.Playlist
import com.yt8492.asmrplayer.data.model.Track

data class FileExplorerUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val currentPath: String = "",
    val directoryTitle: String? = null,
    val parentPath: String? = null,
    val directories: List<BrowsableDirectory> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val documents: List<DocumentFile> = emptyList(),
    val images: List<ImageFile> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val errorMessage: String? = null,
    val playlistMessage: String? = null,
)
