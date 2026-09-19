package com.yt8492.asmrplayer.ui.fileexplorer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yt8492.asmrplayer.data.local.AppDatabase
import com.yt8492.asmrplayer.data.repository.AddTrackResult
import com.yt8492.asmrplayer.data.repository.FileExplorerRepository
import com.yt8492.asmrplayer.data.repository.FileExplorerRepositoryImpl
import com.yt8492.asmrplayer.data.repository.PlaylistRepository
import com.yt8492.asmrplayer.data.repository.PlaylistRepositoryImpl
import com.yt8492.asmrplayer.data.repository.TrackRepository
import com.yt8492.asmrplayer.data.repository.TrackRepositoryImpl
import com.yt8492.asmrplayer.data.repository.normalizeDirectoryPath
import kotlinx.coroutines.Job
import com.yt8492.asmrplayer.data.repository.DocumentPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
            playlistRepository.observePlaylists().collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
    }

    fun loadContent(directoryPath: String = _uiState.value.currentPath) {
        loadJob?.cancel()
        refreshJob?.cancel()
        val normalizedPath = normalizeDirectoryPath(directoryPath)
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isRefreshing = false,
                    currentPath = normalizedPath,
                    directoryTitle = null,
                    parentPath = if (DocumentPath.isDocumentPath(normalizedPath)) "" else null,
                    directories = emptyList(),
                    tracks = emptyList(),
                    images = emptyList(),
                    errorMessage = null,
                )
            }
            runCatching {
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
            runCatching {
                if (DocumentPath.isDocumentPath(currentPath)) {
                    repository.scanDirectory(currentPath)
                } else if (currentPath.isNotEmpty()) {
                    val scanCompleted = runCatching {
                        repository.scanDirectory(currentPath)
                    }.onFailure { throwable ->
                        if (throwable is CancellationException) throw throwable
                        Timber.w(
                            throwable,
                            "フォルダのメディアスキャンに失敗しました pathLength=%d",
                            currentPath.length,
                        )
                    }.getOrDefault(false)
                    if (!scanCompleted) {
                        Timber.w(
                            "フォルダのメディアスキャンが完了しませんでした pathLength=%d",
                            currentPath.length,
                        )
                    }
                }
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
            runCatching {
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
            runCatching {
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
            runCatching {
                val tracks = trackRepository.getTracksInDirectory(directoryPath)
                if (tracks.isEmpty()) {
                    return@runCatching null
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
            runCatching {
                val tracks = trackRepository.getTracksInDirectory(directoryPath)
                if (tracks.isEmpty()) {
                    return@runCatching null
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

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            val applicationContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val repository = FileExplorerRepositoryImpl(applicationContext)
                    val trackRepository = TrackRepositoryImpl(applicationContext)
                    val playlistRepository = PlaylistRepositoryImpl(
                        AppDatabase.getInstance(applicationContext).playlistDao(),
                    )
                    @Suppress("UNCHECKED_CAST")
                    return FileExplorerViewModel(repository, playlistRepository, trackRepository) as T
                }
            }
        }
    }
}
