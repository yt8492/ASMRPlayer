package com.yt8492.asmrplayer.ui.track

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yt8492.asmrplayer.data.repository.AlbumRepository
import com.yt8492.asmrplayer.data.repository.AlbumRepositoryImpl
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrackListUiState())
    val uiState: StateFlow<TrackListUiState> = _uiState.asStateFlow()

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

    companion object {
        fun provideFactory(context: Context, albumId: Long): ViewModelProvider.Factory {
            val applicationContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val albumRepository = AlbumRepositoryImpl(applicationContext)
                    val trackRepository = TrackRepositoryImpl(applicationContext)
                    @Suppress("UNCHECKED_CAST")
                    return TrackListViewModel(albumId, albumRepository, trackRepository) as T
                }
            }
        }
    }
}
