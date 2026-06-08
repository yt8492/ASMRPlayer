package com.yt8492.asmrplayer.ui.fileexplorer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yt8492.asmrplayer.data.repository.FileExplorerRepository
import com.yt8492.asmrplayer.data.repository.FileExplorerRepositoryImpl
import com.yt8492.asmrplayer.data.repository.normalizeDirectoryPath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FileExplorerViewModel(
    private val repository: FileExplorerRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FileExplorerUiState())
    val uiState: StateFlow<FileExplorerUiState> = _uiState.asStateFlow()

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

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            val applicationContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val repository = FileExplorerRepositoryImpl(applicationContext)
                    @Suppress("UNCHECKED_CAST")
                    return FileExplorerViewModel(repository) as T
                }
            }
        }
    }
}
