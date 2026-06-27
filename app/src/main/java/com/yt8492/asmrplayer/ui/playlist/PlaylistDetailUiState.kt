package com.yt8492.asmrplayer.ui.playlist

import com.yt8492.asmrplayer.data.model.Playlist
import com.yt8492.asmrplayer.data.model.Track

data class PlaylistDetailUiState(
    val isLoading: Boolean = true,
    val playlist: Playlist? = null,
    val tracks: List<Track> = emptyList(),
    val isEditMode: Boolean = false,
    val errorMessage: String? = null,
)
