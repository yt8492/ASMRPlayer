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
import com.yt8492.asmrplayer.data.repository.TrackLoopRepository
import com.yt8492.asmrplayer.data.repository.TrackLoopRepositoryImpl
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val queue: PlaybackQueue,
    private val startTrackId: Long,
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository,
    private val trackLoopRepository: TrackLoopRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private var currentTrackLoopJob: Job? = null
    private var currentTrackId: Long? = null

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

    fun onCurrentTrackChanged(trackId: Long?) {
        if (currentTrackId == trackId) return
        currentTrackId = trackId
        currentTrackLoopJob?.cancel()
        if (trackId == null) {
            _uiState.update { it.copy(currentTrackLoop = null) }
            return
        }
        currentTrackLoopJob = viewModelScope.launch {
            trackLoopRepository.observeTrackLoop(trackId).collectLatest { trackLoop ->
                _uiState.update { it.copy(currentTrackLoop = trackLoop) }
            }
        }
    }

    fun saveTrackLoop(trackId: Long, startMs: Long, endMs: Long) {
        if (startMs >= endMs) return
        viewModelScope.launch {
            trackLoopRepository.saveTrackLoop(trackId, startMs, endMs)
        }
    }

    fun deleteTrackLoop(trackId: Long) {
        viewModelScope.launch {
            trackLoopRepository.deleteTrackLoop(trackId)
        }
    }

    companion object {
        fun provideFactory(context: Context, queue: PlaybackQueue, startTrackId: Long): ViewModelProvider.Factory {
            val applicationContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val database = AppDatabase.getInstance(applicationContext)
                    val trackRepository: TrackRepository = TrackRepositoryImpl(applicationContext)
                    val playlistRepository: PlaylistRepository = PlaylistRepositoryImpl(
                        database.playlistDao(),
                    )
                    val trackLoopRepository: TrackLoopRepository = TrackLoopRepositoryImpl(
                        database.trackLoopDao(),
                    )
                    @Suppress("UNCHECKED_CAST")
                    return PlayerViewModel(
                        queue = queue,
                        startTrackId = startTrackId,
                        trackRepository = trackRepository,
                        playlistRepository = playlistRepository,
                        trackLoopRepository = trackLoopRepository,
                    ) as T
                }
            }
        }
    }
}
