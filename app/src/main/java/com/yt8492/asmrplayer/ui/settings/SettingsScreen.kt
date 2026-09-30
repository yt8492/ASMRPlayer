package com.yt8492.asmrplayer.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yt8492.asmrplayer.data.model.LibraryFolder
import com.yt8492.asmrplayer.data.repository.audioReadPermission
import com.yt8492.asmrplayer.data.repository.hasAudioReadPermission
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsRoute(
    isInitialSetup: Boolean = false,
    onCompleteSetup: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(LocalContext.current)),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var hasAudioPermission by remember { mutableStateOf(context.hasAudioReadPermission()) }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
        if (!granted) viewModel.permissionDenied()
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::addFolder)
    }
    var restoringFolderUri by rememberSaveable { mutableStateOf<String?>(null) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val folderUri = restoringFolderUri
        restoringFolderUri = null
        if (uri != null && folderUri != null) viewModel.restoreFolderAccess(folderUri, uri)
    }
    LifecycleResumeEffect(Unit) {
        hasAudioPermission = context.hasAudioReadPermission()
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }
    SettingsScreen(
        state = state,
        isInitialSetup = isInitialSetup,
        hasAudioPermission = hasAudioPermission,
        onRequestAudioPermission = { audioLauncher.launch(audioReadPermission()) },
        onOpenAppSettings = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        },
        onAddFolder = { folderLauncher.launch(null) },
        onReloadFolder = viewModel::reloadFolder,
        onRestoreFolderAccess = { folder ->
            restoringFolderUri = folder.uri
            val tree = Uri.parse(folder.uri)
            restoreLauncher.launch(DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)))
        },
        onRemoveFolder = viewModel::removeFolder,
        onReloadAll = viewModel::reloadAll,
        onCompleteSetup = onCompleteSetup,
        onBack = onBack,
        onMessageShown = viewModel::consumeMessage,
        bottomBar = bottomBar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    isInitialSetup: Boolean,
    hasAudioPermission: Boolean,
    onRequestAudioPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onAddFolder: () -> Unit,
    onReloadFolder: (LibraryFolder) -> Unit,
    onRestoreFolderAccess: (LibraryFolder) -> Unit,
    onRemoveFolder: (LibraryFolder) -> Unit,
    onReloadAll: () -> Unit,
    onCompleteSetup: () -> Unit,
    onMessageShown: () -> Unit,
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    var removingFolder by remember { mutableStateOf<LibraryFolder?>(null) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if (isInitialSetup) "ライブラリの設定" else "設定") },
                    navigationIcon = {
                        if (!isInitialSetup && onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                            }
                        }
                    },
                )
                if (state.isLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(state.loadingLabel, color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.folders.any { !it.hasPermission }) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("フォルダへのアクセスを許可してください", style = MaterialTheme.typography.titleMedium)
                            Text("再インストールや端末の移行後は、アクセスの再許可が必要です。各フォルダの「アクセスを許可」から、元と同じフォルダを選んでください。")
                        }
                    }
                }
            }
            item {
                Text("聴きたい音声を読み込みましょう", style = MaterialTheme.typography.titleLarge)
                Text("読み込むフォルダはいつでも変更できます。",
                    modifier = Modifier.padding(top = 8.dp))
            }
            item {
                Text("フォルダ", style = MaterialTheme.typography.titleMedium)
                Text("選択したフォルダと、その中のフォルダにある音声・画像・PDF・txtを読み込みます。画像を1枚ずつ選ぶ必要はありません。",
                    modifier = Modifier.padding(vertical = 8.dp))
                OutlinedButton(onClick = onAddFolder, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("フォルダを追加")
                }
                Text("内部ストレージ直下やDownload全体などは選択できません。その中の作品フォルダなどを選んでください。",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            items(state.folders, key = { it.uri }) { folder ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(folder.name, style = MaterialTheme.typography.titleMedium)
                        Text("音声 ${folder.audioCount}件・画像 ${folder.imageCount}件・文書 ${folder.documentCount}件")
                        if (folder.lastScanAt > 0) {
                            Text("最終読み込み：${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(folder.lastScanAt))}",
                                style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("まだ読み込みが完了していません", style = MaterialTheme.typography.bodySmall)
                        }
                        if (!folder.hasPermission) {
                            Text("アクセス許可が必要です")
                        } else {
                            folder.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (folder.hasPermission) {
                                TextButton(onClick = { onReloadFolder(folder) }, enabled = !state.isLoading) { Text("再読み込み") }
                            } else {
                                TextButton(onClick = { onRestoreFolderAccess(folder) }, enabled = !state.isLoading) { Text("アクセスを許可") }
                            }
                            TextButton(onClick = { removingFolder = folder }, enabled = !state.isLoading) { Text("登録解除") }
                        }
                    }
                }
            }
            if (!isInitialSetup) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("以前のプレイリストの再生", style = MaterialTheme.typography.titleMedium)
                            Text("以前のプレイリストにある音声を再生するためのアクセス許可です。")
                            Text(if (hasAudioPermission) "音声へのアクセス：許可済み" else "音声へのアクセス：未許可",
                                style = MaterialTheme.typography.labelLarge)
                            if (!hasAudioPermission) {
                                Button(onClick = onRequestAudioPermission, enabled = !state.isLoading) {
                                    Text("音声へのアクセスを許可")
                                }
                                TextButton(onClick = onOpenAppSettings, enabled = !state.isLoading) { Text("Androidの権限設定を開く") }
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onReloadAll, enabled = !state.isLoading && state.folders.any { it.hasPermission }, modifier = Modifier.fillMaxWidth()) {
                    Text("ライブラリを再読み込み")
                }
                if (isInitialSetup) {
                    Button(onClick = onCompleteSetup, enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("はじめる") }
                    Text("あとから設定画面でフォルダを追加できます。", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
    removingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { removingFolder = null },
            title = { Text("フォルダの登録を解除") },
            text = { Text("「${folder.name}」の読み込みを解除します。元のファイルは削除されません。このフォルダから追加したプレイリストの曲は、再登録するまで利用できなくなります。") },
            confirmButton = { TextButton(onClick = { removingFolder = null; onRemoveFolder(folder) }) { Text("登録解除") } },
            dismissButton = { TextButton(onClick = { removingFolder = null }) { Text("キャンセル") } },
        )
    }
}
