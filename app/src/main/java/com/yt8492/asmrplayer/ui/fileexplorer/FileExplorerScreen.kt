package com.yt8492.asmrplayer.ui.fileexplorer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.yt8492.asmrplayer.R
import com.yt8492.asmrplayer.data.model.BrowsableDirectory
import com.yt8492.asmrplayer.data.model.ImageFile
import com.yt8492.asmrplayer.data.model.Track
import com.yt8492.asmrplayer.ui.common.PlaylistNameDialog
import com.yt8492.asmrplayer.ui.common.SingleLineMarqueeText
import com.yt8492.asmrplayer.ui.common.TrackInfoDialog
import com.yt8492.asmrplayer.ui.common.rememberCurrentPlaybackTrackId
import com.yt8492.asmrplayer.ui.preview.FilePreviewDialog
import com.yt8492.asmrplayer.ui.preview.ImagePreviewDialog
import com.yt8492.asmrplayer.ui.preview.PreviewFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    uiState: FileExplorerUiState,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onDirectoryClick: (String) -> Unit,
    onBack: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onAddTrackToPlaylist: (playlistId: Long, trackId: Long) -> Unit,
    onCreatePlaylistAndAddTrack: (name: String, trackId: Long) -> Unit,
    onCreatePlaylistFromDirectory: (name: String, directoryPath: String) -> Unit,
    onAddDirectoryToPlaylist: (playlistId: Long, directoryPath: String) -> Unit,
    onErrorShown: () -> Unit,
    onPlaylistMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    resetRequestKey: Int = 0,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val currentPlaybackTrackId by rememberCurrentPlaybackTrackId()
    var selectedTrack by remember { mutableStateOf<Track?>(null) }
    var trackForNewPlaylist by remember { mutableStateOf<Track?>(null) }
    var selectedDirectory by remember { mutableStateOf<BrowsableDirectory?>(null) }
    var directoryForNewPlaylist by remember { mutableStateOf<BrowsableDirectory?>(null) }
    var previewImage by remember { mutableStateOf<ImageFile?>(null) }
    var previewFile by rememberSaveable(stateSaver = PreviewFile.Saver) { mutableStateOf<PreviewFile?>(null) }
    var trackForInfo by remember { mutableStateOf<Track?>(null) }
    val isRoot = uiState.currentPath.isEmpty()
    val title = uiState.directoryTitle ?: directoryDisplayTitle(uiState.currentPath, stringResource(R.string.file_explorer_title))

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onErrorShown()
        }
    }
    LaunchedEffect(uiState.playlistMessage) {
        uiState.playlistMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onPlaylistMessageShown()
        }
    }
    LaunchedEffect(resetRequestKey) {
        if (resetRequestKey > 0) {
            selectedTrack = null
            trackForNewPlaylist = null
            selectedDirectory = null
            directoryForNewPlaylist = null
            previewImage = null
            previewFile = null
            trackForInfo = null
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    SingleLineMarqueeText(text = title)
                },
                navigationIcon = {
                    if (!isRoot) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.track_list_back),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "設定")
                    }
                    if (!isRoot) {
                        IconButton(
                            onClick = {
                                selectedDirectory = BrowsableDirectory(
                                    path = uiState.currentPath,
                                    name = title,
                                    itemCount = uiState.tracks.size,
                                )
                            },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = stringResource(id = R.string.playlist_add_folder),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .testTag(FILE_EXPLORER_PULL_TO_REFRESH_TAG),
        ) {
            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                uiState.directories.isEmpty() && uiState.tracks.isEmpty() && uiState.images.isEmpty() && uiState.documents.isEmpty() ->
                    EmptyFileExplorer(
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )

                else -> FileExplorerList(
                    directories = uiState.directories,
                    tracks = uiState.tracks,
                    images = uiState.images,
                    documents = uiState.documents,
                    onDirectoryClick = onDirectoryClick,
                    onFolderPermissionRequired = onOpenSettings,
                    onTrackClick = onTrackClick,
                    onTrackLongClick = { track -> trackForInfo = track },
                    onImageClick = { image -> previewImage = image },
                    onDocumentClick = { document -> previewFile = PreviewFile(document.uri.toString(), document.name, document.kind, document.size) },
                    onAddToPlaylistClick = { track -> selectedTrack = track },
                    onAddDirectoryToPlaylistClick = { directory -> selectedDirectory = directory },
                    currentPlaybackTrackId = currentPlaybackTrackId,
                    resetRequestKey = resetRequestKey,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    selectedTrack?.let { track ->
        PlaylistPickerSheet(
            playlists = uiState.playlists,
            onDismiss = { selectedTrack = null },
            onCreatePlaylist = {
                trackForNewPlaylist = track
                selectedTrack = null
            },
            onPlaylistClick = { playlist ->
                onAddTrackToPlaylist(playlist.id, track.id)
                selectedTrack = null
            },
        )
    }

    selectedDirectory?.let { directory ->
        PlaylistPickerSheet(
            playlists = uiState.playlists,
            onDismiss = { selectedDirectory = null },
            onCreatePlaylist = {
                directoryForNewPlaylist = directory
                selectedDirectory = null
            },
            onPlaylistClick = { playlist ->
                onAddDirectoryToPlaylist(playlist.id, directory.path)
                selectedDirectory = null
            },
        )
    }

    trackForNewPlaylist?.let { track ->
        PlaylistNameDialog(
            title = stringResource(id = R.string.playlist_create),
            confirmText = stringResource(id = R.string.common_create),
            initialName = "",
            onDismiss = { trackForNewPlaylist = null },
            onConfirm = { name ->
                onCreatePlaylistAndAddTrack(name, track.id)
                trackForNewPlaylist = null
            },
        )
    }

    directoryForNewPlaylist?.let { directory ->
        PlaylistNameDialog(
            title = stringResource(id = R.string.playlist_create),
            confirmText = stringResource(id = R.string.common_create),
            initialName = directory.name,
            onDismiss = { directoryForNewPlaylist = null },
            onConfirm = { name ->
                onCreatePlaylistFromDirectory(name, directory.path)
                directoryForNewPlaylist = null
            },
        )
    }

    previewImage?.let { image ->
        ImagePreviewDialog(image = image, onDismiss = { previewImage = null })
    }

    previewFile?.let { file ->
        FilePreviewDialog(
            file = file,
            onDismiss = { previewFile = null },
        )
    }

    trackForInfo?.let { track ->
        TrackInfoDialog(
            track = track,
            onDismiss = { trackForInfo = null },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FileExplorerScreenPreview() {
    FileExplorerScreen(
        uiState = FileExplorerUiState(
            currentPath = "Music/Sample/",
            directories = listOf(
                BrowsableDirectory(path = "Music/Sample/Nested/", name = "Nested", itemCount = 3),
            ),
            tracks = listOf(
                Track(
                    id = 1,
                    title = "サンプルトラック",
                    artist = "サンプルアーティスト",
                    durationMs = 210_000,
                    fileSizeBytes = 12_345_678,
                    trackNumber = 1,
                    uri = android.net.Uri.EMPTY,
                ),
            ),
            images = listOf(
                ImageFile(
                    id = 1,
                    title = "サンプル画像.jpg",
                    uri = android.net.Uri.EMPTY,
                    mimeType = "image/jpeg",
                ),
            ),
        ),
        onOpenSettings = {},
        onRetry = {},
        onRefresh = {},
        onDirectoryClick = {},
        onBack = {},
        onTrackClick = {},
        onAddTrackToPlaylist = { _, _ -> },
        onCreatePlaylistAndAddTrack = { _, _ -> },
        onCreatePlaylistFromDirectory = { _, _ -> },
        onAddDirectoryToPlaylist = { _, _ -> },
        onErrorShown = {},
        onPlaylistMessageShown = {},
    )
}

internal const val FILE_EXPLORER_PULL_TO_REFRESH_TAG = "file_explorer_pull_to_refresh"

internal fun directoryDisplayTitle(path: String, rootTitle: String): String =
    if (path.isEmpty()) rootTitle else "フォルダ"
