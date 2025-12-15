package com.yt8492.asmrplayer.ui.track

import com.yt8492.asmrplayer.data.model.Album
import com.yt8492.asmrplayer.data.model.Track

data class TrackListUiState(
    val isLoading: Boolean = false,
    val album: Album? = null,
    val tracks: List<Track> = emptyList(),
    val errorMessage: String? = null,
)
