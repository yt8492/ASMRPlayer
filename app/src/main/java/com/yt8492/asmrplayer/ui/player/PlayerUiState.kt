package com.yt8492.asmrplayer.ui.player

import com.yt8492.asmrplayer.data.model.Track

data class PlayerUiState(
    val isLoading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val startIndex: Int = 0,
    val errorMessage: String? = null,
)
