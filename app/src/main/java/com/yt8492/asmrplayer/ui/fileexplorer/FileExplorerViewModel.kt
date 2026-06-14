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
import com.yt8492.asmrplayer.data.repository.normalizeDirectoryPath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FileExplorerViewModel(
    private val repository: FileExplorerRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FileExplorerUiState())
    val uiState: StateFlow<FileExplorerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.observePlaylists().collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
    }

    fun loadContent(directoryPath: String = _uiState.value.currentPath) {
        if (_uiState.value.isLoading) return
        val normalizedPath = normalizeDirectoryPath(directoryPath)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    currentPath = normalizedPath,
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
                        directories = content.directories,
                        tracks = content.tracks,
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "ファイルの取得に失敗しました",
                    )
                }
            }
        }
    }

    fun openDirectory(directoryPath: String) {
        loadContent(directoryPath)
    }

    fun openParentDirectory() {
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
                    AddTrackResult.AlreadyExists -> "このトラックは既に追加されています"
                }
                _uiState.update { it.copy(playlistMessage = message) }
            }.onFailure {
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
            }.onFailure {
                _uiState.update { it.copy(playlistMessage = "プレイリストの作成に失敗しました") }
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
                    val playlistRepository = PlaylistRepositoryImpl(
                        AppDatabase.getInstance(applicationContext).playlistDao(),
                    )
                    @Suppress("UNCHECKED_CAST")
                    return FileExplorerViewModel(repository, playlistRepository) as T
                }
            }
        }
    }
}
