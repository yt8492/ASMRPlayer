package com.yt8492.asmrplayer.ui.fileexplorer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yt8492.asmrplayer.R
import com.yt8492.asmrplayer.data.model.AudioDirectory
import com.yt8492.asmrplayer.data.model.ImageFile
import com.yt8492.asmrplayer.data.model.Playlist
import com.yt8492.asmrplayer.data.model.Track
import coil.compose.AsyncImage
import java.util.concurrent.TimeUnit

@Composable
fun FileExplorerRoute(
    onTrackClick: (directoryPath: String, directoryTitle: String, tracks: List<Track>, index: Int) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    viewModel: FileExplorerViewModel = viewModel(
        factory = FileExplorerViewModel.provideFactory(LocalContext.current),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val rootTitle = stringResource(id = R.string.file_explorer_title)
    val permissions = remember { fileExplorerPermissions() }
    var mediaPermissionState by remember { mutableStateOf(context.currentMediaPermissionState()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        mediaPermissionState = context.currentMediaPermissionState()
        if (mediaPermissionState.hasAnyPermission) {
            viewModel.loadContent()
        }
    }

    LaunchedEffect(mediaPermissionState.hasAnyPermission) {
        mediaPermissionState = context.currentMediaPermissionState()
        if (mediaPermissionState.hasAnyPermission) {
            viewModel.loadContent()
        }
    }

    BackHandler(enabled = uiState.currentPath.isNotEmpty()) {
        viewModel.openParentDirectory()
    }

    FileExplorerScreen(
        uiState = uiState,
        hasPermission = mediaPermissionState.hasAnyPermission,
        hasMissingPermission = mediaPermissionState.hasMissingPermission,
        onRequestPermission = { permissionLauncher.launch(permissions) },
        onRetry = viewModel::loadContent,
        onDirectoryClick = viewModel::openDirectory,
        onBack = viewModel::openParentDirectory,
        onTrackClick = { index ->
            val directoryTitle = uiState.currentPath.trim('/').substringAfterLast(
                delimiter = '/',
                missingDelimiterValue = rootTitle,
            ).ifEmpty { rootTitle }
            onTrackClick(uiState.currentPath, directoryTitle, uiState.tracks, index)
        },
        onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
        onCreatePlaylistAndAddTrack = viewModel::createPlaylistAndAddTrack,
        onErrorShown = viewModel::consumeError,
        onPlaylistMessageShown = viewModel::consumePlaylistMessage,
        bottomBar = bottomBar,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    uiState: FileExplorerUiState,
    hasPermission: Boolean,
    hasMissingPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onDirectoryClick: (String) -> Unit,
    onBack: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onAddTrackToPlaylist: (playlistId: Long, trackId: Long) -> Unit,
    onCreatePlaylistAndAddTrack: (name: String, trackId: Long) -> Unit,
    onErrorShown: () -> Unit,
    onPlaylistMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTrack by remember { mutableStateOf<Track?>(null) }
    var trackForNewPlaylist by remember { mutableStateOf<Track?>(null) }
    var previewImage by remember { mutableStateOf<ImageFile?>(null) }
    val isRoot = uiState.currentPath.isEmpty()
    val title = uiState.currentPath.trim('/').substringAfterLast(
        delimiter = '/',
        missingDelimiterValue = stringResource(id = R.string.file_explorer_title),
    ).ifEmpty { stringResource(id = R.string.file_explorer_title) }

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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
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
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            when {
                !hasPermission -> PermissionRequest(
                    onRequestPermission = onRequestPermission,
                    modifier = Modifier.align(Alignment.Center),
                )

                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                uiState.directories.isEmpty() && uiState.tracks.isEmpty() && uiState.images.isEmpty() -> EmptyFileExplorer(
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> FileExplorerList(
                    directories = uiState.directories,
                    tracks = uiState.tracks,
                    images = uiState.images,
                    hasMissingPermission = hasMissingPermission,
                    onRequestPermission = onRequestPermission,
                    onDirectoryClick = onDirectoryClick,
                    onTrackClick = onTrackClick,
                    onImageClick = { image -> previewImage = image },
                    onAddToPlaylistClick = { track -> selectedTrack = track },
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

    trackForNewPlaylist?.let { track ->
        CreatePlaylistDialog(
            onDismiss = { trackForNewPlaylist = null },
            onConfirm = { name ->
                onCreatePlaylistAndAddTrack(name, track.id)
                trackForNewPlaylist = null
            },
        )
    }

    previewImage?.let { image ->
        ImagePreviewDialog(
            image = image,
            onDismiss = { previewImage = null },
        )
    }
}

@Composable
private fun PermissionRequest(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = R.string.file_explorer_permission_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(id = R.string.file_explorer_permission_description),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onRequestPermission) {
            Text(text = stringResource(id = R.string.album_permission_button))
        }
    }
}

@Composable
private fun EmptyFileExplorer(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = R.string.file_explorer_empty),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onRetry) {
            Text(text = stringResource(id = R.string.common_retry))
        }
    }
}

@Composable
private fun FileExplorerList(
    directories: List<AudioDirectory>,
    tracks: List<Track>,
    images: List<ImageFile>,
    hasMissingPermission: Boolean,
    onRequestPermission: () -> Unit,
    onDirectoryClick: (String) -> Unit,
    onTrackClick: (Int) -> Unit,
    onImageClick: (ImageFile) -> Unit,
    onAddToPlaylistClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        if (hasMissingPermission) {
            item(key = "missing-permission") {
                MissingPermissionItem(onRequestPermission = onRequestPermission)
                HorizontalDivider()
            }
        }
        items(
            items = directories,
            key = { it.path },
        ) { directory ->
            ListItem(
                modifier = Modifier.clickable { onDirectoryClick(directory.path) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = stringResource(id = R.string.file_explorer_folder_content_description),
                    )
                },
                headlineContent = {
                    Text(
                        text = directory.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.file_explorer_item_count, directory.trackCount))
                },
            )
            HorizontalDivider()
        }
        itemsIndexed(
            items = tracks,
            key = { _, track -> track.id },
        ) { index, track ->
            ListItem(
                modifier = Modifier.clickable { onTrackClick(index) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                    )
                },
                headlineContent = {
                    Text(
                        text = track.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        text = track.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatDuration(track.durationMs),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        IconButton(onClick = { onAddToPlaylistClick(track) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = stringResource(id = R.string.playlist_add_track),
                            )
                        }
                    }
                },
            )
            HorizontalDivider()
        }
        items(
            items = images,
            key = { image -> "image-${image.id}" },
        ) { image ->
            ListItem(
                modifier = Modifier.clickable { onImageClick(image) },
                leadingContent = {
                    AsyncImage(
                        model = image.uri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop,
                        placeholder = rememberVectorPainter(Icons.Filled.Image),
                        error = rememberVectorPainter(Icons.Filled.Image),
                        fallback = rememberVectorPainter(Icons.Filled.Image),
                    )
                },
                headlineContent = {
                    Text(
                        text = image.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        text = image.mimeType.ifEmpty { stringResource(id = R.string.file_explorer_image_preview) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun MissingPermissionItem(
    onRequestPermission: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(text = stringResource(id = R.string.file_explorer_missing_permission_title))
        },
        supportingContent = {
            Text(text = stringResource(id = R.string.file_explorer_missing_permission_description))
        },
        trailingContent = {
            TextButton(onClick = onRequestPermission) {
                Text(text = stringResource(id = R.string.album_permission_button))
            }
        },
    )
}

@Composable
private fun ImagePreviewDialog(
    image: ImageFile,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 16.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Text(
                text = image.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            AsyncImage(
                model = image.uri,
                contentDescription = stringResource(id = R.string.file_explorer_image_preview),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp),
                contentScale = ContentScale.Fit,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(id = R.string.file_explorer_image_close))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPickerSheet(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(id = R.string.playlist_select_title),
                style = MaterialTheme.typography.titleMedium,
            )
            FilledTonalButton(
                onClick = onCreatePlaylist,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                )
                Text(text = stringResource(id = R.string.playlist_create))
            }
            if (playlists.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.playlist_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                playlists.forEach { playlist ->
                    ListItem(
                        modifier = Modifier.clickable { onPlaylistClick(playlist) },
                        headlineContent = {
                            Text(
                                text = playlist.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.playlist_track_count, playlist.trackCount))
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.playlist_create)) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(text = stringResource(id = R.string.playlist_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(text = stringResource(id = R.string.common_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.common_cancel))
            }
        },
    )
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Preview(showBackground = true)
@Composable
private fun FileExplorerScreenPreview() {
    FileExplorerScreen(
        uiState = FileExplorerUiState(
            currentPath = "Music/Sample/",
            directories = listOf(
                AudioDirectory(path = "Music/Sample/Nested/", name = "Nested", trackCount = 3),
            ),
            tracks = listOf(
                Track(
                    id = 1,
                    title = "サンプルトラック",
                    artist = "サンプルアーティスト",
                    durationMs = 210_000,
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
        hasPermission = true,
        hasMissingPermission = false,
        onRequestPermission = {},
        onRetry = {},
        onDirectoryClick = {},
        onBack = {},
        onTrackClick = {},
        onAddTrackToPlaylist = { _, _ -> },
        onCreatePlaylistAndAddTrack = { _, _ -> },
        onErrorShown = {},
        onPlaylistMessageShown = {},
    )
}

private data class MediaPermissionState(
    val hasAnyPermission: Boolean,
    val hasMissingPermission: Boolean,
)

private fun fileExplorerPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        buildList {
            add(Manifest.permission.READ_MEDIA_AUDIO)
            add(Manifest.permission.READ_MEDIA_IMAGES)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
        }.toTypedArray()
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun android.content.Context.currentMediaPermissionState(): MediaPermissionState {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        val hasStoragePermission = hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        return MediaPermissionState(
            hasAnyPermission = hasStoragePermission,
            hasMissingPermission = !hasStoragePermission,
        )
    }

    val hasAudioPermission = hasPermission(Manifest.permission.READ_MEDIA_AUDIO)
    val hasFullImagePermission = hasPermission(Manifest.permission.READ_MEDIA_IMAGES)
    val hasSelectedImagePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    val hasImagePermission = hasFullImagePermission || hasSelectedImagePermission
    return MediaPermissionState(
        hasAnyPermission = hasAudioPermission || hasImagePermission,
        hasMissingPermission = !hasAudioPermission || !hasImagePermission,
    )
}

private fun android.content.Context.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
