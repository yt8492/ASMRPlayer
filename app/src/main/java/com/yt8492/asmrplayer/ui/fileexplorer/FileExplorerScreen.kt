package com.yt8492.asmrplayer.ui.fileexplorer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.yt8492.asmrplayer.data.model.Track
import java.util.concurrent.TimeUnit

@Composable
fun FileExplorerRoute(
    onTrackClick: (directoryPath: String, directoryTitle: String, tracks: List<Track>, index: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FileExplorerViewModel = viewModel(
        factory = FileExplorerViewModel.provideFactory(LocalContext.current),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val rootTitle = stringResource(id = R.string.file_explorer_title)
    val permission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.loadContent()
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.loadContent()
        }
    }

    BackHandler(enabled = uiState.currentPath.isNotEmpty()) {
        viewModel.openParentDirectory()
    }

    FileExplorerScreen(
        uiState = uiState,
        hasPermission = hasPermission,
        onRequestPermission = { permissionLauncher.launch(permission) },
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
        onErrorShown = viewModel::consumeError,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    uiState: FileExplorerUiState,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onDirectoryClick: (String) -> Unit,
    onBack: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
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

                uiState.directories.isEmpty() && uiState.tracks.isEmpty() -> EmptyFileExplorer(
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> FileExplorerList(
                    directories = uiState.directories,
                    tracks = uiState.tracks,
                    onDirectoryClick = onDirectoryClick,
                    onTrackClick = onTrackClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
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
            text = stringResource(id = R.string.album_permission_title),
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
            Text(text = stringResource(id = R.string.album_list_retry))
        }
    }
}

@Composable
private fun FileExplorerList(
    directories: List<AudioDirectory>,
    tracks: List<Track>,
    onDirectoryClick: (String) -> Unit,
    onTrackClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
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
                    Text(text = stringResource(id = R.string.album_track_count, directory.trackCount))
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
                    }
                },
            )
            HorizontalDivider()
        }
    }
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
        ),
        hasPermission = true,
        onRequestPermission = {},
        onRetry = {},
        onDirectoryClick = {},
        onBack = {},
        onTrackClick = {},
        onErrorShown = {},
    )
}
