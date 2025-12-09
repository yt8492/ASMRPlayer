package com.yt8492.asmrplayer.ui.album

import com.yt8492.asmrplayer.data.model.Album

data class AlbumListUiState(
    val isLoading: Boolean = false,
    val albums: List<Album> = emptyList(),
    val errorMessage: String? = null,
)
