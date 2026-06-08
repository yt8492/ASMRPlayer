package com.yt8492.asmrplayer.ui.playlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yt8492.asmrplayer.data.local.AppDatabase
import com.yt8492.asmrplayer.data.repository.PlaylistRepository
import com.yt8492.asmrplayer.data.repository.PlaylistRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlaylistListViewModel(
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlaylistListUiState())
    val uiState: StateFlow<PlaylistListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.observePlaylists().collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
    }

    fun createPlaylist(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                playlistRepository.createPlaylist(trimmedName)
            }.onFailure {
                _uiState.update { state ->
                    state.copy(errorMessage = "プレイリストの作成に失敗しました")
                }
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            runCatching {
                playlistRepository.deletePlaylist(playlistId)
            }.onFailure {
                _uiState.update { state ->
                    state.copy(errorMessage = "プレイリストの削除に失敗しました")
                }
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            val applicationContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val repository = PlaylistRepositoryImpl(
                        AppDatabase.getInstance(applicationContext).playlistDao(),
                    )
                    @Suppress("UNCHECKED_CAST")
                    return PlaylistListViewModel(repository) as T
                }
            }
        }
    }
}
