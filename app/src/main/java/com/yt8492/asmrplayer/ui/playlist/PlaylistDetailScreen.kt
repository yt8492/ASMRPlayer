package com.yt8492.asmrplayer.ui.playlist

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yt8492.asmrplayer.R
import com.yt8492.asmrplayer.data.model.Track
import java.util.concurrent.TimeUnit

@Composable
fun PlaylistDetailRoute(
    playlistId: Long,
    onBack: () -> Unit,
    onTrackClick: (tracks: List<Track>, index: Int) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    viewModel: PlaylistDetailViewModel = viewModel(
        factory = PlaylistDetailViewModel.provideFactory(LocalContext.current, playlistId),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
    }

    LaunchedEffect(hasPermission, playlistId) {
        if (hasPermission) {
            viewModel.loadPlaylistTracks()
        }
    }

    PlaylistDetailScreen(
        uiState = uiState,
        hasPermission = hasPermission,
        onRequestPermission = { permissionLauncher.launch(permission) },
        onBack = onBack,
        onTrackClick = { index -> onTrackClick(uiState.tracks, index) },
        onRenamePlaylist = viewModel::renamePlaylist,
        onRemoveTrack = viewModel::removeTrack,
        onMoveTrack = viewModel::moveTrack,
        onDragFinished = viewModel::saveCurrentOrder,
        onToggleEditMode = viewModel::toggleEditMode,
        onErrorShown = viewModel::consumeError,
        bottomBar = bottomBar,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    uiState: PlaylistDetailUiState,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onRenamePlaylist: (String) -> Unit,
    onRemoveTrack: (Long) -> Unit,
    onMoveTrack: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragFinished: () -> Unit,
    onToggleEditMode: () -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var isRenameDialogVisible by remember { mutableStateOf(false) }
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
                title = {
                    Text(
                        text = uiState.playlist?.name ?: stringResource(id = R.string.playlist_detail_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.track_list_back),
                        )
                    }
                },
                actions = {
                    if (uiState.isEditMode && uiState.playlist != null) {
                        IconButton(onClick = { isRenameDialogVisible = true }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(id = R.string.playlist_rename),
                            )
                        }
                    }
                    TextButton(onClick = onToggleEditMode) {
                        Text(
                            text = stringResource(
                                id = if (uiState.isEditMode) R.string.common_done else R.string.common_edit,
                            ),
                        )
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

                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                uiState.tracks.isEmpty() -> Text(
                    text = stringResource(id = R.string.playlist_detail_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> ReorderableTrackList(
                    tracks = uiState.tracks,
                    isEditMode = uiState.isEditMode,
                    onTrackClick = onTrackClick,
                    onRemoveTrack = onRemoveTrack,
                    onMoveTrack = onMoveTrack,
                    onDragFinished = onDragFinished,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (isRenameDialogVisible) {
        PlaylistNameDialog(
            title = stringResource(id = R.string.playlist_rename),
            initialName = uiState.playlist?.name.orEmpty(),
            onDismiss = { isRenameDialogVisible = false },
            onConfirm = { name ->
                onRenamePlaylist(name)
                isRenameDialogVisible = false
            },
            confirmText = stringResource(id = R.string.common_save),
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
            text = stringResource(id = R.string.album_permission_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(id = R.string.album_permission_description),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onRequestPermission) {
            Text(text = stringResource(id = R.string.album_permission_button))
        }
    }
}

@Composable
private fun ReorderableTrackList(
    tracks: List<Track>,
    isEditMode: Boolean,
    onTrackClick: (Int) -> Unit,
    onRemoveTrack: (Long) -> Unit,
    onMoveTrack: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draggingTrackId by remember { mutableStateOf<Long?>(null) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }
    val latestLastIndex by rememberUpdatedState(tracks.lastIndex)
    val itemHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }

    LazyColumn(modifier = modifier) {
        itemsIndexed(
            items = tracks,
            key = { _, track -> track.id },
        ) { index, track ->
            var currentIndex by remember(track.id) { mutableIntStateOf(index) }
            ListItem(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = if (draggingTrackId == track.id) draggingOffset else 0f
                    }
                    .then(
                        if (isEditMode) {
                            Modifier
                        } else {
                            Modifier.clickable { onTrackClick(index) }
                        },
                    ),
                leadingContent = if (isEditMode) {
                    {
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = stringResource(id = R.string.playlist_reorder),
                            modifier = Modifier.pointerInput(track.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingTrackId = track.id
                                        draggingOffset = 0f
                                        currentIndex = index
                                    },
                                    onDragEnd = {
                                        draggingTrackId = null
                                        draggingOffset = 0f
                                        onDragFinished()
                                    },
                                    onDragCancel = {
                                        draggingTrackId = null
                                        draggingOffset = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        draggingOffset += dragAmount.y
                                        while (draggingOffset > itemHeightPx && currentIndex < latestLastIndex) {
                                            onMoveTrack(currentIndex, currentIndex + 1)
                                            currentIndex += 1
                                            draggingOffset -= itemHeightPx
                                        }
                                        while (draggingOffset < -itemHeightPx && currentIndex > 0) {
                                            onMoveTrack(currentIndex, currentIndex - 1)
                                            currentIndex -= 1
                                            draggingOffset += itemHeightPx
                                        }
                                    },
                                )
                            },
                        )
                    }
                } else {
                    null
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
                        if (isEditMode) {
                            IconButton(onClick = { onRemoveTrack(track.id) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(id = R.string.playlist_track_remove),
                                )
                            }
                        }
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
