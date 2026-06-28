package com.yt8492.asmrplayer.ui.player

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yt8492.asmrplayer.data.local.AppDatabase
import com.yt8492.asmrplayer.data.model.PlaylistTrack
import com.yt8492.asmrplayer.data.model.Track
import com.yt8492.asmrplayer.data.repository.PlaylistRepository
import com.yt8492.asmrplayer.data.repository.PlaylistRepositoryImpl
import com.yt8492.asmrplayer.data.repository.PlaylistTrackOrder
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
    context: Context,
    private val queue: PlaybackQueue,
    private val startTrackId: Long,
    private val startPlaylistTrackId: Long?,
    private val startIndexHint: Int?,
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository,
    private val trackLoopRepository: TrackLoopRepository,
    private val trackArtworkRepository: TrackArtworkRepository,
    private val queueArtworkRepository: QueueArtworkRepository,
) : ViewModel() {
    private val contentResolver: ContentResolver = context.applicationContext.contentResolver
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
                    is PlaybackQueue.Album -> {
                        val tracks = trackRepository.getTracks(currentQueue.albumId)
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
                        val tracks = trackRepository.getTracks(playlistTracks.map { it.trackId })
                        val tracksById = tracks.associateBy { it.id }
                        val queueItems = playlistTracks.mapNotNull { playlistTrack ->
                            tracksById[playlistTrack.trackId]?.let { track ->
                                PlayerQueueItem(queueItemId = playlistTrack.id, track = track)
                            }
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
                        tracks = loadedTracks.queueItems.map { queueItem -> queueItem.track },
                        startIndex = loadedTracks.startIndex,
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
                    tracks = movedQueueItems.map { queueItem -> queueItem.track },
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
            contentResolver.releasePersistableUriPermission(
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
        fun provideFactory(
            context: Context,
            queue: PlaybackQueue,
            startTrackId: Long,
            startPlaylistTrackId: Long? = null,
            startIndexHint: Int? = null,
        ): ViewModelProvider.Factory {
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
                        startPlaylistTrackId = startPlaylistTrackId,
                        startIndexHint = startIndexHint,
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

private data class LoadedTracks(
    val queueItems: List<PlayerQueueItem>,
    val startIndex: Int,
)

internal fun resolvePlaybackStartIndex(
    tracks: List<Track>,
    startTrackId: Long,
    startIndexHint: Int?,
): Int {
    return resolvePlaybackStartIndexByTrackIds(
        trackIds = tracks.map { it.id },
        startTrackId = startTrackId,
        startIndexHint = startIndexHint,
    )
}

internal fun resolvePlaybackStartIndexByTrackIds(
    trackIds: List<Long>,
    startTrackId: Long,
    startIndexHint: Int?,
): Int {
    return startIndexHint
        ?.takeIf { it in trackIds.indices && trackIds[it] == startTrackId }
        ?: trackIds.indexOfFirst { it == startTrackId }.takeIf { it >= 0 }
        ?: 0
}

internal fun resolvePlaylistPlaybackStartIndex(
    tracks: List<Track>,
    playlistTracks: List<PlaylistTrack>,
    startTrackId: Long,
    startPlaylistTrackId: Long?,
    startIndexHint: Int?,
): Int {
    return resolvePlaylistPlaybackStartIndexByTrackIds(
        trackIds = tracks.map { it.id },
        playlistTracks = playlistTracks,
        startTrackId = startTrackId,
        startPlaylistTrackId = startPlaylistTrackId,
        startIndexHint = startIndexHint,
    )
}

internal fun resolvePlaylistPlaybackStartIndexByTrackIds(
    trackIds: List<Long>,
    playlistTracks: List<PlaylistTrack>,
    startTrackId: Long,
    startPlaylistTrackId: Long?,
    startIndexHint: Int?,
): Int {
    val availableTrackIds = trackIds.toSet()
    val availablePlaylistTracks = playlistTracks.filter { it.trackId in availableTrackIds }
    val playlistTrackIndex = startPlaylistTrackId
        ?.let { targetId -> availablePlaylistTracks.indexOfFirst { it.id == targetId } }
        ?.takeIf { it >= 0 }

    return playlistTrackIndex ?: resolvePlaybackStartIndexByTrackIds(
        trackIds = trackIds,
        startTrackId = startTrackId,
        startIndexHint = startIndexHint,
    )
}
