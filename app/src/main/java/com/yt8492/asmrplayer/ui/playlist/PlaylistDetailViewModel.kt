package com.yt8492.asmrplayer.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yt8492.asmrplayer.core.coroutines.runSuspendCatching
import com.yt8492.asmrplayer.data.model.PlaylistTrackOrder
import com.yt8492.asmrplayer.data.repository.PlaylistRepository
import com.yt8492.asmrplayer.domain.ResolvePlaylistTracks
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class PlaylistDetailViewModel(
    private val playlistId: Long,
    private val playlistRepository: PlaylistRepository,
    private val resolvePlaylistTracks: ResolvePlaylistTracks,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun loadPlaylistTracks() {
        if (loadJob?.isActive == true) return
        if (!_uiState.value.isLoading && _uiState.value.playlist != null) return
        loadJob = viewModelScope.launch {
            playlistRepository.observePlaylistTracks(playlistId) .catch { error ->
                Timber.e(error, "プレイリストの取得に失敗しました")
                _uiState.update { it.copy(isLoading = false, errorMessage = "プレイリストの取得に失敗しました") }
            }.collect { playlistTracks ->
                runSuspendCatching {
                    val playlist = playlistRepository.getPlaylist(playlistId)
                    val playlistTrackItems = resolvePlaylistTracks(playlistTracks)
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
                }.onFailure { throwable ->
                    Timber.e(throwable, "プレイリスト詳細の取得に失敗しました playlistId=%d", playlistId)
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
            runSuspendCatching {
                playlistRepository.removeTrack(playlistId, playlistTrackId)
            }.onFailure { throwable ->
                Timber.e(
                    throwable,
                    "プレイリストからのトラック削除に失敗しました playlistId=%d playlistTrackId=%d",
                    playlistId,
                    playlistTrackId,
                )
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
            runSuspendCatching {
                playlistRepository.renamePlaylist(playlistId, trimmedName)
            }.onFailure { throwable ->
                Timber.e(throwable, "プレイリスト名の変更に失敗しました playlistId=%d", playlistId)
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
            runSuspendCatching {
                playlistRepository.replaceTrackOrder(playlistId, playlistTrackIds)
            }.onFailure { throwable ->
                Timber.e(throwable, "プレイリスト曲順の保存に失敗しました playlistId=%d", playlistId)
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

}
