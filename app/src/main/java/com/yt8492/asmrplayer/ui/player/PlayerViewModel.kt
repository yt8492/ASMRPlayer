package com.yt8492.asmrplayer.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yt8492.asmrplayer.core.coroutines.runSuspendCatching
import com.yt8492.asmrplayer.data.model.PlaylistTrackOrder
import com.yt8492.asmrplayer.data.repository.ArtworkRepository
import com.yt8492.asmrplayer.data.repository.PlaylistRepository
import com.yt8492.asmrplayer.data.repository.TrackLoopRepository
import com.yt8492.asmrplayer.data.repository.TrackRepository
import com.yt8492.asmrplayer.domain.ResolvePlaylistTracks
import com.yt8492.asmrplayer.playback.model.PlaybackQueue
import com.yt8492.asmrplayer.playback.model.artworkTarget
import com.yt8492.asmrplayer.playback.model.logType
import com.yt8492.asmrplayer.playback.model.resolvePlaybackStartIndex
import com.yt8492.asmrplayer.playback.model.resolvePlaylistPlaybackStartIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class PlayerViewModel(
    private val queue: PlaybackQueue,
    private val startTrackId: Long,
    private val startPlaylistTrackId: Long?,
    private val startIndexHint: Int?,
    private val trackRepository: TrackRepository,
    private val resolvePlaylistTracks: ResolvePlaylistTracks,
    private val playlistRepository: PlaylistRepository,
    private val trackLoopRepository: TrackLoopRepository,
    private val artworkRepository: ArtworkRepository,
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
            runSuspendCatching {
                when (val currentQueue = queue) {
                    is PlaybackQueue.Folder -> {
                        val tracks = trackRepository.getTracksInDirectory(currentQueue.directoryPath)
                        LoadedTracks(
                            queueItems = tracks.map { track ->
                                PlayerQueueItem(queueItemId = track.id, track = track)
                            },
                            startIndex = resolvePlaybackStartIndex(
                                tracks = tracks,
                                startTrackId = startTrackId,
                                startIndexHint = startIndexHint,
                            ),
                        )
                    }

                    is PlaybackQueue.Playlist -> {
                        val playlistTracks = playlistRepository.getPlaylistTracks(currentQueue.playlistId)
                        val queueItems = resolvePlaylistTracks(playlistTracks).map {
                            PlayerQueueItem(queueItemId = it.playlistTrackId, track = it.track)
                        }
                        LoadedTracks(
                            queueItems = queueItems,
                            startIndex = resolvePlaylistPlaybackStartIndex(
                                tracks = queueItems.map { it.track },
                                playlistTracks = playlistTracks,
                                startTrackId = startTrackId,
                                startPlaylistTrackId = startPlaylistTrackId,
                                startIndexHint = startIndexHint,
                            ),
                        )
                    }
                }
            }.onSuccess { loadedTracks ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        queueItems = loadedTracks.queueItems,
                        startIndex = loadedTracks.startIndex,
                    )
                }
            }.onFailure { throwable ->
                Timber.e(throwable, "再生キューのトラック取得に失敗しました queueType=%s", queue.logType())
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "トラックの取得に失敗しました",
                    )
                }
            }
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        _uiState.update { currentState ->
            val currentQueueItems = currentState.queueItems
            val movedQueueItemIds = PlaylistTrackOrder.move(
                itemIds = currentQueueItems.map { it.queueItemId },
                fromIndex = fromIndex,
                toIndex = toIndex,
            )
            if (movedQueueItemIds == currentQueueItems.map { it.queueItemId }) {
                currentState
            } else {
                val queueItemsById = currentQueueItems.associateBy { it.queueItemId }
                val movedQueueItems = movedQueueItemIds.mapNotNull { queueItemsById[it] }
                currentState.copy(
                    queueItems = movedQueueItems,
                )
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
            trackLoopRepository.observeTrackLoop(trackId) .catch { error ->
                Timber.e(error, "ABリピート範囲の取得に失敗しました")
                _uiState.update { it.copy(errorMessage = "ABリピート範囲の取得に失敗しました") }
            }.collectLatest { trackLoop ->
                _uiState.update { it.copy(currentTrackLoop = trackLoop) }
            }
        }
        currentTrackArtworkJob = viewModelScope.launch {
            artworkRepository.observeTrackArtwork(trackId) .catch { error ->
                Timber.e(error, "トラック画像の取得に失敗しました")
                _uiState.update { it.copy(errorMessage = "トラック画像の取得に失敗しました") }
            }.collectLatest { trackArtwork ->
                _uiState.update { it.copy(currentTrackArtworkUri = trackArtwork) }
            }
        }
    }

    fun saveTrackLoop(trackId: Long, startMs: Long, endMs: Long) {
        if (startMs >= endMs) return
        viewModelScope.launch {
            runSuspendCatching {
                trackLoopRepository.saveTrackLoop(trackId, startMs, endMs)
            }.onFailure { throwable ->
                Timber.e(throwable, "ABリピート範囲の保存に失敗しました trackId=%d", trackId)
            }
        }
    }

    fun deleteTrackLoop(trackId: Long) {
        viewModelScope.launch {
            runSuspendCatching {
                trackLoopRepository.deleteTrackLoop(trackId)
            }.onFailure { throwable ->
                Timber.e(throwable, "ABリピート範囲の削除に失敗しました trackId=%d", trackId)
            }
        }
    }

    fun saveTrackArtwork(trackId: Long, imageUri: Uri) = updateArtwork("トラック画像の保存に失敗しました") {
        artworkRepository.saveTrackArtwork(trackId, imageUri)
        if (currentTrackId == trackId) _uiState.update { it.copy(currentTrackArtworkUri = imageUri) }
    }

    fun deleteTrackArtwork(trackId: Long) = updateArtwork("トラック画像の削除に失敗しました") {
        artworkRepository.deleteTrackArtwork(trackId)
        if (currentTrackId == trackId) _uiState.update { it.copy(currentTrackArtworkUri = null) }
    }

    fun saveQueueArtwork(imageUri: Uri) = updateArtwork("再生キュー画像の保存に失敗しました") {
        artworkRepository.saveQueueArtwork(queue.artworkTarget(), imageUri)
        _uiState.update { it.copy(queueArtworkUri = imageUri) }
    }

    fun deleteQueueArtwork() = updateArtwork("再生キュー画像の削除に失敗しました") {
        artworkRepository.deleteQueueArtwork(queue.artworkTarget())
        _uiState.update { it.copy(queueArtworkUri = null) }
    }

    private fun updateArtwork(message: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            runSuspendCatching { action() }.onFailure { error ->
                Timber.e(error, message)
                _uiState.update { it.copy(errorMessage = message) }
            }
        }
    }

    fun consumeError() { _uiState.update { it.copy(errorMessage = null) } }

    private fun observeQueueArtwork() {
        val target = queue.artworkTarget()
        queueArtworkJob?.cancel()
        queueArtworkJob = viewModelScope.launch {
            artworkRepository.observeQueueArtwork(target) .catch { error ->
                Timber.e(error, "再生キュー画像の取得に失敗しました")
                _uiState.update { it.copy(errorMessage = "再生キュー画像の取得に失敗しました") }
            }.collectLatest { queueArtwork ->
                _uiState.update { it.copy(queueArtworkUri = queueArtwork) }
            }
        }
    }

}

private data class LoadedTracks(
    val queueItems: List<PlayerQueueItem>,
    val startIndex: Int,
)
