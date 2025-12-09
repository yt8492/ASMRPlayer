package com.yt8492.asmrplayer.ui.track

import com.yt8492.asmrplayer.data.model.Track

data class TrackListUiState(
    val isLoading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val errorMessage: String? = null,
)
