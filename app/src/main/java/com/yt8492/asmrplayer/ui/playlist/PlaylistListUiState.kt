package com.yt8492.asmrplayer.ui.playlist

import com.yt8492.asmrplayer.data.model.Playlist

data class PlaylistListUiState(
    val playlists: List<Playlist> = emptyList(),
    val errorMessage: String? = null,
)
