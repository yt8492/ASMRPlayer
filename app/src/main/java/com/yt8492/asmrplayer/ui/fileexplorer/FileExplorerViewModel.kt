package com.yt8492.asmrplayer.ui.fileexplorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yt8492.asmrplayer.core.coroutines.runSuspendCatching
import com.yt8492.asmrplayer.data.repository.AddTrackResult
import com.yt8492.asmrplayer.data.repository.FileExplorerRepository
import com.yt8492.asmrplayer.data.repository.PlaylistRepository
import com.yt8492.asmrplayer.data.repository.TrackRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class FileExplorerViewModel(
    private val repository: FileExplorerRepository,
    private val playlistRepository: PlaylistRepository,
    private val trackRepository: TrackRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FileExplorerUiState())
    val uiState: StateFlow<FileExplorerUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var refreshJob: Job? = null

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

    fun loadContent(directoryPath: String = _uiState.value.currentPath) {
        loadJob?.cancel()
        refreshJob?.cancel()
        val normalizedPath = directoryPath.trim().trimEnd('/').let { if (it.isEmpty()) "" else "$it/" }
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isRefreshing = false,
                    currentPath = normalizedPath,
                    directoryTitle = null,
                    parentPath = if (normalizedPath.isNotEmpty()) "" else null,
                    directories = emptyList(),
                    tracks = emptyList(),
                    images = emptyList(),
                    documents = emptyList(),
                    errorMessage = null,
                )
            }
            runSuspendCatching {
                repository.getContent(normalizedPath)
            }.onSuccess { content ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentPath = content.currentPath,
                        directoryTitle = content.directoryTitle,
                        parentPath = content.parentPath,
                        directories = content.directories,
                        tracks = content.tracks,
                        images = content.images,
                        documents = content.documents,
                    )
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Timber.e(throwable, "ファイル一覧の取得に失敗しました pathLength=%d", normalizedPath.length)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "フォルダを読み込めません。設定でアクセス許可と保存先を確認してください。",
                    )
                }
            }
        }
    }

    fun refreshContent() {
        if (_uiState.value.isLoading || _uiState.value.isRefreshing) return
        val currentPath = _uiState.value.currentPath
        refreshJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    errorMessage = null,
                )
            }
            runSuspendCatching {
                repository.scanDirectory(currentPath)
                repository.getContent(currentPath)
            }.onSuccess { content ->
                _uiState.update { state ->
                    if (state.currentPath != currentPath) {
                        state.copy(isRefreshing = false)
                    } else {
                        state.copy(
                            isRefreshing = false,
                            currentPath = content.currentPath,
                            directoryTitle = content.directoryTitle,
                            parentPath = content.parentPath,
                            directories = content.directories,
                            tracks = content.tracks,
                            images = content.images,
                            documents = content.documents,
                        )
                    }
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Timber.e(throwable, "ファイル一覧の再取得に失敗しました pathLength=%d", currentPath.length)
                _uiState.update { state ->
                    state.copy(
                        isRefreshing = false,
                        errorMessage = if (state.currentPath == currentPath) {
                            "ファイルの取得に失敗しました"
                        } else {
                            state.errorMessage
                        },
                    )
                }
            }
        }
    }

    fun openDirectory(directoryPath: String) {
        loadContent(directoryPath)
    }

    fun resetToInitialState() {
        loadContent("")
    }

    fun openParentDirectory() {
        _uiState.value.parentPath?.let {
            loadContent(it)
            return
        }
        val currentPath = _uiState.value.currentPath.trim('/')
        if (currentPath.isEmpty()) return
        val parentPath = currentPath.substringBeforeLast('/', missingDelimiterValue = "")
        loadContent(parentPath)
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            runSuspendCatching {
                playlistRepository.addTrack(playlistId, trackId)
            }.onSuccess { result ->
                val message = when (result) {
                    AddTrackResult.Added -> "プレイリストに追加しました"
                }
                _uiState.update { it.copy(playlistMessage = message) }
            }.onFailure { throwable ->
                Timber.e(
                    throwable,
                    "トラックのプレイリスト追加に失敗しました playlistId=%d trackId=%d",
                    playlistId,
                    trackId,
                )
                _uiState.update { it.copy(playlistMessage = "プレイリストへの追加に失敗しました") }
            }
        }
    }

    fun createPlaylistAndAddTrack(name: String, trackId: Long) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        viewModelScope.launch {
            runSuspendCatching {
                val playlistId = playlistRepository.createPlaylist(trimmedName)
                playlistRepository.addTrack(playlistId, trackId)
            }.onSuccess {
                _uiState.update { it.copy(playlistMessage = "プレイリストを作成して追加しました") }
            }.onFailure { throwable ->
                Timber.e(throwable, "プレイリスト作成後のトラック追加に失敗しました trackId=%d", trackId)
                _uiState.update { it.copy(playlistMessage = "プレイリストの作成に失敗しました") }
            }
        }
    }

    fun createPlaylistFromDirectory(name: String, directoryPath: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        viewModelScope.launch {
            runSuspendCatching {
                val tracks = trackRepository.getTracksInDirectory(directoryPath)
                if (tracks.isEmpty()) {
                    return@runSuspendCatching null
                }
                val playlistId = playlistRepository.createPlaylist(trimmedName)
                playlistRepository.addTracks(
                    playlistId = playlistId,
                    trackIds = tracks.map { it.id },
                )
            }.onSuccess { result ->
                val message = if (result == null) {
                    "このフォルダに追加できる音声ファイルがありません"
                } else {
                    "プレイリストを作成して${result.addedCount}曲追加しました"
                }
                _uiState.update { it.copy(playlistMessage = message) }
            }.onFailure { throwable ->
                Timber.e(
                    throwable,
                    "フォルダからのプレイリスト作成に失敗しました pathLength=%d",
                    directoryPath.length,
                )
                _uiState.update { it.copy(playlistMessage = "プレイリストの作成に失敗しました") }
            }
        }
    }

    fun addDirectoryToPlaylist(playlistId: Long, directoryPath: String) {
        viewModelScope.launch {
            runSuspendCatching {
                val tracks = trackRepository.getTracksInDirectory(directoryPath)
                if (tracks.isEmpty()) {
                    return@runSuspendCatching null
                }
                playlistRepository.addTracks(
                    playlistId = playlistId,
                    trackIds = tracks.map { it.id },
                )
            }.onSuccess { result ->
                val message = when {
                    result == null -> "このフォルダに追加できる音声ファイルがありません"
                    else -> "プレイリストに${result.addedCount}曲追加しました"
                }
                _uiState.update { it.copy(playlistMessage = message) }
            }.onFailure { throwable ->
                Timber.e(
                    throwable,
                    "フォルダ内トラックのプレイリスト追加に失敗しました playlistId=%d pathLength=%d",
                    playlistId,
                    directoryPath.length,
                )
                _uiState.update { it.copy(playlistMessage = "プレイリストへの追加に失敗しました") }
            }
        }
    }

    fun consumePlaylistMessage() {
        _uiState.update { it.copy(playlistMessage = null) }
    }

}
