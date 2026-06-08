package com.yt8492.asmrplayer.ui.track

import com.yt8492.asmrplayer.data.model.Album
import com.yt8492.asmrplayer.data.model.Playlist
import com.yt8492.asmrplayer.data.model.Track

data class TrackListUiState(
    val isLoading: Boolean = false,
    val album: Album? = null,
    val tracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val errorMessage: String? = null,
    val playlistMessage: String? = null,
)
