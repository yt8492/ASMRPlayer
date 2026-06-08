package com.yt8492.asmrplayer.ui.track

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yt8492.asmrplayer.data.local.AppDatabase
import com.yt8492.asmrplayer.data.repository.AlbumRepository
import com.yt8492.asmrplayer.data.repository.AlbumRepositoryImpl
import com.yt8492.asmrplayer.data.repository.AddTrackResult
import com.yt8492.asmrplayer.data.repository.PlaylistRepository
import com.yt8492.asmrplayer.data.repository.PlaylistRepositoryImpl
import com.yt8492.asmrplayer.data.repository.TrackRepository
import com.yt8492.asmrplayer.data.repository.TrackRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TrackListViewModel(
    private val albumId: Long,
    private val albumRepository: AlbumRepository,
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrackListUiState())
    val uiState: StateFlow<TrackListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.observePlaylists().collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
    }

    fun loadTracks() {
        if (_uiState.value.isLoading) {
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }
            runCatching {
                val album = albumRepository.getAlbumById(albumId)
                val tracks = trackRepository.getTracks(albumId)
                album to tracks
            }.onSuccess { (album, tracks) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        album = album,
                        tracks = tracks,
                    )
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = "トラックの取得に失敗しました",
                    )
                }
            }
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            runCatching {
                playlistRepository.addTrack(playlistId, trackId)
            }.onSuccess { result ->
                val message = when (result) {
                    AddTrackResult.Added -> "プレイリストに追加しました"
                    AddTrackResult.AlreadyExists -> "このトラックは既に追加されています"
                }
                _uiState.update { it.copy(playlistMessage = message) }
            }.onFailure {
                _uiState.update { it.copy(playlistMessage = "プレイリストへの追加に失敗しました") }
            }
        }
    }

    fun createPlaylistAndAddTrack(name: String, trackId: Long) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val playlistId = playlistRepository.createPlaylist(trimmedName)
                playlistRepository.addTrack(playlistId, trackId)
            }.onSuccess {
                _uiState.update { it.copy(playlistMessage = "プレイリストを作成して追加しました") }
            }.onFailure {
                _uiState.update { it.copy(playlistMessage = "プレイリストの作成に失敗しました") }
            }
        }
    }

    fun consumePlaylistMessage() {
        _uiState.update { it.copy(playlistMessage = null) }
    }

    companion object {
        fun provideFactory(context: Context, albumId: Long): ViewModelProvider.Factory {
            val applicationContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val albumRepository = AlbumRepositoryImpl(applicationContext)
                    val trackRepository = TrackRepositoryImpl(applicationContext)
                    val playlistRepository = PlaylistRepositoryImpl(
                        AppDatabase.getInstance(applicationContext).playlistDao(),
                    )
                    @Suppress("UNCHECKED_CAST")
                    return TrackListViewModel(albumId, albumRepository, trackRepository, playlistRepository) as T
                }
            }
        }
    }
}
