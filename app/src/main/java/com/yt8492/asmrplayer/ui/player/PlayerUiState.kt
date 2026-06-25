package com.yt8492.asmrplayer.ui.player

import android.net.Uri
import com.yt8492.asmrplayer.data.model.Track
import com.yt8492.asmrplayer.data.model.TrackLoop

data class PlayerUiState(
    val isLoading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val startIndex: Int = 0,
    val currentTrackLoop: TrackLoop? = null,
    val currentTrackArtworkUri: Uri? = null,
    val queueArtworkUri: Uri? = null,
    val errorMessage: String? = null,
)
