package com.yt8492.asmrplayer.ui.track

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yt8492.asmrplayer.R
import com.yt8492.asmrplayer.data.model.Playlist
import com.yt8492.asmrplayer.data.model.Track
import coil.compose.AsyncImage
import com.yt8492.asmrplayer.data.model.Album
import java.util.concurrent.TimeUnit

@Composable
fun TrackListRoute(
    albumId: Long,
    onBack: () -> Unit,
    onTrackClick: (tracks: List<Track>, index: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrackListViewModel = viewModel(
        factory = TrackListViewModel.provideFactory(LocalContext.current, albumId),
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
        if (isGranted) {
            viewModel.loadTracks()
        }
    }

    LaunchedEffect(hasPermission, albumId) {
        if (hasPermission) {
            viewModel.loadTracks()
        }
    }

    TrackListScreen(
        uiState = uiState,
        hasPermission = hasPermission,
        onRequestPermission = { permissionLauncher.launch(permission) },
        onRetry = viewModel::loadTracks,
        onBack = onBack,
        onTrackClick = { index -> onTrackClick(uiState.tracks, index) },
        onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
        onCreatePlaylistAndAddTrack = viewModel::createPlaylistAndAddTrack,
        onPlaylistMessageShown = viewModel::consumePlaylistMessage,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackListScreen(
    uiState: TrackListUiState,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onTrackClick: (index: Int) -> Unit,
    onAddTrackToPlaylist: (playlistId: Long, trackId: Long) -> Unit,
    onCreatePlaylistAndAddTrack: (name: String, trackId: Long) -> Unit,
    onPlaylistMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTrack by remember { mutableStateOf<Track?>(null) }
    var trackForNewPlaylist by remember { mutableStateOf<Track?>(null) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = uiState.album?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.track_list_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
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

                uiState.tracks.isEmpty() -> EmptyTrackList(
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )

                uiState.album != null -> TrackList(
                    tracks = uiState.tracks,
                    album = uiState.album,
                    onTrackClick = onTrackClick,
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
private fun EmptyTrackList(
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
            text = stringResource(id = R.string.track_list_empty),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onRetry) {
            Text(text = stringResource(id = R.string.track_list_retry))
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    album: Album,
    onTrackClick: (index: Int) -> Unit,
    onAddToPlaylistClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        item {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AlbumArt(
                    albumId = album.id,
                    albumArtUri = album.albumArtUri,
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(id = R.string.album_track_count, tracks.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        itemsIndexed(
            items = tracks,
            key = { _, track -> track.id },
        ) { index, track ->
            val displayTrackNumber = if (track.trackNumber > 0) {
                track.trackNumber % 1000
            } else {
                null
            }
            ListItem(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onTrackClick(index) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Text(
                        text = displayTrackNumber?.toString() ?: (index + 1).toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                headlineContent = {
                    Text(
                        text = track.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                supportingContent = {
                    Text(
                        text = track.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            HorizontalDivider(
                modifier = Modifier.padding(start = 64.dp, end = 24.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            )
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

@Composable
private fun AlbumArt(
    albumId: Long,
    albumArtUri: Uri?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val context = LocalContext.current
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
        val bitmap = runCatching {
            context.contentResolver.loadThumbnail(uri, Size(1024, 1024), null)
        }.getOrNull()
        AsyncImage(
            model = bitmap,
            contentDescription = contentDescription,
            modifier = modifier
                .aspectRatio(1.45f)
                .clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.Crop,
            placeholder = rememberVectorPainter(Icons.Filled.Album),
            error = rememberVectorPainter(Icons.Filled.Album),
            fallback = rememberVectorPainter(Icons.Filled.Album),
        )
    } else {
        AsyncImage(
            model = albumArtUri,
            contentDescription = contentDescription,
            modifier = modifier
                .aspectRatio(1.45f)
                .clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.Crop,
            placeholder = rememberVectorPainter(Icons.Filled.Album),
            error = rememberVectorPainter(Icons.Filled.Album),
            fallback = rememberVectorPainter(Icons.Filled.Album),
        )
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
private fun TrackListScreenPreview() {
    TrackListScreen(
        uiState = TrackListUiState(
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
        onBack = {},
        onTrackClick = {},
        onAddTrackToPlaylist = { _, _ -> },
        onCreatePlaylistAndAddTrack = { _, _ -> },
        onPlaylistMessageShown = {},
    )
}
