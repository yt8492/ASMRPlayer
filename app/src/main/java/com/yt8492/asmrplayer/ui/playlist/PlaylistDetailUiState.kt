package com.yt8492.asmrplayer.ui.playlist

import com.yt8492.asmrplayer.data.model.Playlist
import com.yt8492.asmrplayer.data.model.Track

data class PlaylistDetailUiState(
    val isLoading: Boolean = true,
    val playlist: Playlist? = null,
    val playlistTracks: List<PlaylistTrackItem> = emptyList(),
    val isEditMode: Boolean = false,
    val errorMessage: String? = null,
) {
    val tracks: List<Track>
        get() = playlistTracks.map { it.track }
}

data class PlaylistTrackItem(
    val playlistTrackId: Long,
    val track: Track,
)
