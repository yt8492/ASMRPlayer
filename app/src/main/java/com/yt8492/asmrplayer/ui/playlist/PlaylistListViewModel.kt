package com.yt8492.asmrplayer.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yt8492.asmrplayer.core.coroutines.runSuspendCatching
import com.yt8492.asmrplayer.data.repository.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class PlaylistListViewModel(
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlaylistListUiState())
    val uiState: StateFlow<PlaylistListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.observePlaylists() .catch { error ->
                Timber.e(error, "プレイリストの取得に失敗しました")
                _uiState.update { it.copy(errorMessage = "プレイリストの取得に失敗しました") }
            }.collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
    }

    fun createPlaylist(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        viewModelScope.launch {
            runSuspendCatching {
                playlistRepository.createPlaylist(trimmedName)
            }.onFailure { throwable ->
                Timber.e(throwable, "プレイリストの作成に失敗しました")
                _uiState.update { state ->
                    state.copy(errorMessage = "プレイリストの作成に失敗しました")
                }
            }
        }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
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

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            runSuspendCatching {
                playlistRepository.deletePlaylist(playlistId)
            }.onFailure { throwable ->
                Timber.e(throwable, "プレイリストの削除に失敗しました playlistId=%d", playlistId)
                _uiState.update { state ->
                    state.copy(errorMessage = "プレイリストの削除に失敗しました")
                }
            }
        }
    }

    fun toggleEditMode() {
        _uiState.update { it.copy(isEditMode = !it.isEditMode) }
    }

    fun resetToInitialState() {
        _uiState.update {
            it.copy(
                isEditMode = false,
                errorMessage = null,
            )
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

}
