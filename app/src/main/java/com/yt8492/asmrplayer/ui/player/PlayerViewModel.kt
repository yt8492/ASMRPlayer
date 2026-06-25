package com.yt8492.asmrplayer.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yt8492.asmrplayer.data.local.AppDatabase
import com.yt8492.asmrplayer.data.repository.PlaylistRepository
import com.yt8492.asmrplayer.data.repository.PlaylistRepositoryImpl
import com.yt8492.asmrplayer.data.repository.QueueArtworkRepository
import com.yt8492.asmrplayer.data.repository.QueueArtworkRepositoryImpl
import com.yt8492.asmrplayer.data.repository.TrackArtworkRepository
import com.yt8492.asmrplayer.data.repository.TrackArtworkRepositoryImpl
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
    private val context: Context,
    private val queue: PlaybackQueue,
    private val startTrackId: Long,
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository,
    private val trackLoopRepository: TrackLoopRepository,
    private val trackArtworkRepository: TrackArtworkRepository,
    private val queueArtworkRepository: QueueArtworkRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private var currentTrackLoopJob: Job? = null
    private var currentTrackArtworkJob: Job? = null
    private var queueArtworkJob: Job? = null
    private var currentTrackId: Long? = null

    init {
        loadTracks()
        observeQueueArtwork()
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
        currentTrackArtworkJob?.cancel()
        _uiState.update { it.copy(currentTrackArtworkUri = null) }
        if (trackId == null) {
            _uiState.update { it.copy(currentTrackLoop = null, currentTrackArtworkUri = null) }
            return
        }
        currentTrackLoopJob = viewModelScope.launch {
            trackLoopRepository.observeTrackLoop(trackId).collectLatest { trackLoop ->
                _uiState.update { it.copy(currentTrackLoop = trackLoop) }
            }
        }
        currentTrackArtworkJob = viewModelScope.launch {
            trackArtworkRepository.observeTrackArtwork(trackId).collectLatest { trackArtwork ->
                _uiState.update { it.copy(currentTrackArtworkUri = trackArtwork?.imageUri) }
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

    fun saveTrackArtwork(trackId: Long, imageUri: Uri) {
        viewModelScope.launch {
            val previousUri = trackArtworkRepository.getTrackArtwork(trackId)?.imageUri
            trackArtworkRepository.saveTrackArtwork(trackId, imageUri)
            if (previousUri != null && previousUri != imageUri) {
                releaseArtworkPermissionIfUnused(previousUri)
            }
        }
    }

    fun deleteTrackArtwork(trackId: Long) {
        viewModelScope.launch {
            val previousUri = trackArtworkRepository.getTrackArtwork(trackId)?.imageUri
            trackArtworkRepository.deleteTrackArtwork(trackId)
            previousUri?.let { releaseArtworkPermissionIfUnused(it) }
        }
    }

    fun saveQueueArtwork(imageUri: Uri) {
        val target = queue.artworkTarget() ?: return
        viewModelScope.launch {
            val previousUri = queueArtworkRepository.getQueueArtwork(target.queueType, target.queueKey)?.imageUri
            queueArtworkRepository.saveQueueArtwork(target.queueType, target.queueKey, imageUri)
            if (previousUri != null && previousUri != imageUri) {
                releaseArtworkPermissionIfUnused(previousUri)
            }
        }
    }

    fun deleteQueueArtwork() {
        val target = queue.artworkTarget() ?: return
        viewModelScope.launch {
            val previousUri = queueArtworkRepository.getQueueArtwork(target.queueType, target.queueKey)?.imageUri
            queueArtworkRepository.deleteQueueArtwork(target.queueType, target.queueKey)
            previousUri?.let { releaseArtworkPermissionIfUnused(it) }
        }
    }

    private fun observeQueueArtwork() {
        val target = queue.artworkTarget()
        if (target == null) {
            _uiState.update { it.copy(queueArtworkUri = null) }
            return
        }
        queueArtworkJob?.cancel()
        queueArtworkJob = viewModelScope.launch {
            queueArtworkRepository.observeQueueArtwork(target.queueType, target.queueKey).collectLatest { queueArtwork ->
                _uiState.update { it.copy(queueArtworkUri = queueArtwork?.imageUri) }
            }
        }
    }

    private suspend fun releaseArtworkPermissionIfUnused(imageUri: Uri) {
        val isUsed = trackArtworkRepository.isImageUriUsed(imageUri) ||
            queueArtworkRepository.isImageUriUsed(imageUri)
        if (isUsed) return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                imageUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun PlaybackQueue.artworkTarget(): QueueArtworkTarget? {
        return when (this) {
            is PlaybackQueue.Album -> QueueArtworkTarget(QUEUE_TYPE_ALBUM, albumId.toString())
            is PlaybackQueue.Playlist -> QueueArtworkTarget(QUEUE_TYPE_PLAYLIST, playlistId.toString())
            is PlaybackQueue.Folder -> QueueArtworkTarget(QUEUE_TYPE_FOLDER, directoryPath)
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
                    val trackArtworkRepository: TrackArtworkRepository = TrackArtworkRepositoryImpl(
                        database.trackArtworkDao(),
                    )
                    val queueArtworkRepository: QueueArtworkRepository = QueueArtworkRepositoryImpl(
                        database.queueArtworkDao(),
                    )
                    @Suppress("UNCHECKED_CAST")
                    return PlayerViewModel(
                        context = applicationContext,
                        queue = queue,
                        startTrackId = startTrackId,
                        trackRepository = trackRepository,
                        playlistRepository = playlistRepository,
                        trackLoopRepository = trackLoopRepository,
                        trackArtworkRepository = trackArtworkRepository,
                        queueArtworkRepository = queueArtworkRepository,
                    ) as T
                }
            }
        }

        private const val QUEUE_TYPE_ALBUM = "album"
        private const val QUEUE_TYPE_PLAYLIST = "playlist"
        private const val QUEUE_TYPE_FOLDER = "folder"
    }
}

private data class QueueArtworkTarget(
    val queueType: String,
    val queueKey: String,
)
