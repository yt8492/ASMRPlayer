package com.yt8492.asmrplayer.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yt8492.asmrplayer.data.model.LibraryFolder
import com.yt8492.asmrplayer.data.repository.LibraryFolderRepository
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

    fun addFolder(uri: Uri) = perform("フォルダを読み込んでいます") {
        folders.addFolder(uri)
        "フォルダを読み込みました"
    }

    fun reloadFolder(folder: LibraryFolder) = perform("「${folder.name}」を読み込んでいます") {
        folders.reloadFolder(folder.uri)
        "フォルダを再読み込みしました"
    }

    fun removeFolder(folder: LibraryFolder) = perform("フォルダの登録を解除しています") {
        folders.removeFolder(folder.uri)
        "フォルダの登録を解除しました"
    }

    fun reloadAll() = perform("ライブラリを読み込んでいます") {
        var failures = 0
        folders.getFolders().forEach { folder ->
            _uiState.update { it.copy(loadingLabel = "「${folder.name}」を読み込んでいます") }
            try {
                folders.reloadFolder(folder.uri)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                failures += 1
            }
        }
        if (failures == 0) "ライブラリを再読み込みしました" else "${failures}件のフォルダを読み込めませんでした。各フォルダの表示を確認してください。"
    }

    fun permissionDenied() {
        _uiState.update { it.copy(message = "以前のプレイリストにある音声の再生にはアクセス許可が必要です。") }
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
                _uiState.update { it.copy(message = "読み込み設定を更新できませんでした。アクセス許可と保存先の接続を確認してください。") }
            } finally {
                _uiState.update { it.copy(isLoading = false, loadingLabel = "") }
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
