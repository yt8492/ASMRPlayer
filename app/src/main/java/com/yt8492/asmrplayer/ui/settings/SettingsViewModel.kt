package com.yt8492.asmrplayer.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yt8492.asmrplayer.data.model.LibraryFolder
import com.yt8492.asmrplayer.data.repository.LibraryFolderRepository
import com.yt8492.asmrplayer.data.repository.DifferentFolderSelectedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val folders: List<LibraryFolder> = emptyList(),
    val isLoading: Boolean = false,
    val loadingLabel: String = "",
    val message: String? = null,
)

class SettingsViewModel(
    private val folders: LibraryFolderRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            folders.observeFolders().collect { values -> _uiState.update { it.copy(folders = values) } }
        }
    }

    fun refreshPermissions() {
        viewModelScope.launch {
            val values = folders.getFolders()
            _uiState.update { it.copy(folders = values) }
        }
    }

    fun addFolder(uri: Uri) = perform("読み込み中") {
        folders.addFolder(uri)
        "フォルダを読み込みました"
    }

    fun reloadFolder(folder: LibraryFolder) = perform("「${folder.name}」を読み込み中") {
        folders.reloadFolder(folder.uri)
        "再読み込みしました"
    }

    fun restoreFolderAccess(folderUri: String, selectedUri: Uri) = perform("アクセスを復旧中") {
        folders.restoreFolderAccess(folderUri, selectedUri)
        "アクセスを復旧しました"
    }

    fun removeFolder(folder: LibraryFolder) = perform("登録を解除中") {
        folders.removeFolder(folder.uri)
        "登録を解除しました"
    }

    fun reloadAll() = perform("読み込み中") {
        var failures = 0
        val registered = folders.getFolders()
        val pendingCount = registered.count { !it.hasPermission }
        registered.filter { it.hasPermission }.forEach { folder ->
            _uiState.update { it.copy(loadingLabel = "「${folder.name}」を読み込み中") }
            try {
                folders.reloadFolder(folder.uri)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                failures += 1
            }
        }
        when {
            failures > 0 -> "${failures}件の読み込みに失敗しました。"
            pendingCount > 0 -> "再読み込みしました。${pendingCount}件はアクセス許可が必要です。"
            else -> "再読み込みしました"
        }
    }

    fun permissionDenied() {
        _uiState.update { it.copy(message = "音声へのアクセス許可が必要です。") }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun perform(label: String, action: suspend () -> String) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, loadingLabel = label, message = null) }
        viewModelScope.launch {
            try {
                val message = action()
                _uiState.update { it.copy(message = message) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val message = when (error) {
                    is DifferentFolderSelectedException -> "元と同じフォルダを選んでください。"
                    is SecurityException -> "フォルダへのアクセスを許可してください。"
                    else -> "更新できませんでした。権限と保存先の接続を確認してください。"
                }
                _uiState.update { it.copy(message = message) }
            } finally {
                val values = folders.getFolders()
                _uiState.update { it.copy(folders = values, isLoading = false, loadingLabel = "") }
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(
                LibraryFolderRepository(context.applicationContext),
            ) as T
        }
    }
}
