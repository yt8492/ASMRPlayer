package com.yt8492.asmrplayer.ui.playlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yt8492.asmrplayer.data.local.AppDatabase
import com.yt8492.asmrplayer.data.repository.PlaylistRepository
import com.yt8492.asmrplayer.data.repository.PlaylistRepositoryImpl
import com.yt8492.asmrplayer.data.repository.PlaylistTrackOrder
import com.yt8492.asmrplayer.data.repository.TrackRepository
import com.yt8492.asmrplayer.data.repository.TrackRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlaylistDetailViewModel(
    private val playlistId: Long,
    private val playlistRepository: PlaylistRepository,
    private val trackRepository: TrackRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    fun loadPlaylistTracks() {
        if (!_uiState.value.isLoading && _uiState.value.playlist != null) return
        viewModelScope.launch {
            playlistRepository.observePlaylistTracks(playlistId).collect { playlistTracks ->
                runCatching {
                    val playlist = playlistRepository.getPlaylist(playlistId)
                    val trackIds = playlistTracks.map { it.trackId }
                    val tracks = trackRepository.getTracks(trackIds)
                    val tracksById = tracks.associateBy { it.id }
                    val playlistTrackItems = playlistTracks.mapNotNull { playlistTrack ->
                        tracksById[playlistTrack.trackId]?.let { track ->
                            PlaylistTrackItem(
                                playlistTrackId = playlistTrack.id,
                                track = track,
                            )
                        }
                    }
                    playlist to playlistTrackItems
                }.onSuccess { (playlist, playlistTrackItems) ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            playlist = playlist,
                            playlistTracks = playlistTrackItems,
                            errorMessage = null,
                        )
                    }
                }.onFailure {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = "プレイリストの取得に失敗しました",
                        )
                    }
                }
            }
        }
    }

    fun removeTrack(playlistTrackId: Long) {
        val currentTracks = _uiState.value.playlistTracks.filterNot { it.playlistTrackId == playlistTrackId }
        _uiState.update { it.copy(playlistTracks = currentTracks) }
        viewModelScope.launch {
            runCatching {
                playlistRepository.removeTrack(playlistId, playlistTrackId)
            }.onFailure {
                _uiState.update { state ->
                    state.copy(errorMessage = "トラックの削除に失敗しました")
                }
            }
        }
    }

    fun renamePlaylist(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                playlistRepository.renamePlaylist(playlistId, trimmedName)
            }.onFailure {
                _uiState.update { state ->
                    state.copy(errorMessage = "プレイリスト名の変更に失敗しました")
                }
            }
        }
    }

    fun moveTrack(fromIndex: Int, toIndex: Int) {
        val currentTracks = _uiState.value.playlistTracks
        val movedPlaylistTrackIds = PlaylistTrackOrder.move(
            itemIds = currentTracks.map { it.playlistTrackId },
            fromIndex = fromIndex,
            toIndex = toIndex,
        )
        val tracksByPlaylistTrackId = currentTracks.associateBy { it.playlistTrackId }
        val movedTracks = movedPlaylistTrackIds.mapNotNull { tracksByPlaylistTrackId[it] }
        _uiState.update { it.copy(playlistTracks = movedTracks) }
    }

    fun saveCurrentOrder() {
        val playlistTrackIds = _uiState.value.playlistTracks.map { it.playlistTrackId }
        viewModelScope.launch {
            runCatching {
                playlistRepository.replaceTrackOrder(playlistId, playlistTrackIds)
            }.onFailure {
                _uiState.update { state ->
                    state.copy(errorMessage = "曲順の保存に失敗しました")
                }
            }
        }
    }

    fun toggleEditMode() {
        _uiState.update { it.copy(isEditMode = !it.isEditMode) }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        fun provideFactory(context: Context, playlistId: Long): ViewModelProvider.Factory {
            val applicationContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val database = AppDatabase.getInstance(applicationContext)
                    val playlistRepository = PlaylistRepositoryImpl(database.playlistDao())
                    val trackRepository = TrackRepositoryImpl(applicationContext)
                    @Suppress("UNCHECKED_CAST")
                    return PlaylistDetailViewModel(playlistId, playlistRepository, trackRepository) as T
                }
            }
        }
    }
}
