package com.yt8492.asmrplayer.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yt8492.asmrplayer.data.local.AppDatabase
import com.yt8492.asmrplayer.data.repository.PlaylistRepository
import com.yt8492.asmrplayer.data.repository.PlaylistRepositoryImpl
import com.yt8492.asmrplayer.data.repository.TrackRepository
import com.yt8492.asmrplayer.data.repository.TrackRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val queue: PlaybackQueue,
    private val startTrackId: Long,
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        loadTracks()
    }

    private fun loadTracks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                when (val currentQueue = queue) {
                    is PlaybackQueue.Album -> trackRepository.getTracks(currentQueue.albumId)
                    is PlaybackQueue.Folder -> trackRepository.getTracksInDirectory(currentQueue.directoryPath)
                    is PlaybackQueue.Playlist -> {
                        val trackIds = playlistRepository.getTrackIds(currentQueue.playlistId)
                        trackRepository.getTracks(trackIds)
                    }
                }
            }.onSuccess { tracks ->
                val startIndex = tracks.indexOfFirst { it.id == startTrackId }.takeIf { it >= 0 } ?: 0
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        tracks = tracks,
                        startIndex = startIndex,
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "トラックの取得に失敗しました",
                    )
                }
            }
        }
    }

    companion object {
        fun provideFactory(context: Context, queue: PlaybackQueue, startTrackId: Long): ViewModelProvider.Factory {
            val applicationContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val trackRepository: TrackRepository = TrackRepositoryImpl(applicationContext)
                    val playlistRepository: PlaylistRepository = PlaylistRepositoryImpl(
                        AppDatabase.getInstance(applicationContext).playlistDao(),
                    )
                    @Suppress("UNCHECKED_CAST")
                    return PlayerViewModel(queue, startTrackId, trackRepository, playlistRepository) as T
                }
            }
        }
    }
}
