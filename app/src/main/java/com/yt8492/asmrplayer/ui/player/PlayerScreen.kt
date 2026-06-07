package com.yt8492.asmrplayer.ui.player

import android.content.ComponentName
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.yt8492.asmrplayer.R
import com.yt8492.asmrplayer.service.PlaybackService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

@Composable
fun PlayerRoute(
    albumId: Long,
    albumTitle: String,
    albumArtUri: Uri?,
    startTrackId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModel.provideFactory(LocalContext.current, albumId, startTrackId),
    ),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionToken = remember {
        SessionToken(context, ComponentName(context, PlaybackService::class.java))
    }
    val controllerFuture = remember {
        MediaController.Builder(context, sessionToken).buildAsync()
    }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var controllerError by remember { mutableStateOf(false) }

    DisposableEffect(controllerFuture) {
        controllerFuture.addListener(
            {
                runCatching {
                    controllerFuture.get()
                }.onSuccess {
                    controller = it
                }.onFailure {
                    controllerError = true
                }
            },
            mainExecutor,
        )
        onDispose {
            controllerFuture.cancel(true)
            controller?.release()
        }
    }

    LaunchedEffect(controller, uiState.tracks, uiState.startIndex) {
        val ctl = controller ?: return@LaunchedEffect
        if (uiState.tracks.isEmpty()) return@LaunchedEffect
        val mediaIds = uiState.tracks.map { it.id.toString() }
        val currentMediaIds = List(ctl.mediaItemCount) { index ->
            ctl.getMediaItemAt(index).mediaId
        }
        val startIndex = uiState.startIndex.takeIf { it in uiState.tracks.indices } ?: 0
        if (currentMediaIds == mediaIds) {
            if (ctl.currentMediaItem?.mediaId != startTrackId.toString()) {
                ctl.seekTo(startIndex, 0)
                ctl.play()
            }
            return@LaunchedEffect
        }

        val mediaItems = uiState.tracks.map { track ->
            MediaItem.Builder()
                .setUri(track.uri)
                .setMediaId(track.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(albumTitle)
                        .setExtras(
                            Bundle().apply {
                                putLong(PlaybackService.EXTRA_ALBUM_ID, albumId)
                                putLong(PlaybackService.EXTRA_TRACK_ID, track.id)
                                putString(PlaybackService.EXTRA_ALBUM_TITLE, albumTitle)
                                putString(PlaybackService.EXTRA_ALBUM_ART_URI, albumArtUri?.toString().orEmpty())
                            },
                        )
                        .build(),
                )
                .build()
        }
        ctl.setMediaItems(mediaItems)
        ctl.seekTo(startIndex, 0)
        ctl.prepare()
        ctl.playWhenReady = true
    }

    if (controllerError) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = stringResource(id = R.string.player_connection_error))
        }
    } else if (controller == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else {
        PlayerScreen(
            player = controller!!,
            uiState = uiState,
            albumId = albumId,
            albumTitle = albumTitle,
            albumArtUri = albumArtUri,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    player: Player,
    uiState: PlayerUiState,
    albumId: Long?,
    albumTitle: String,
    albumArtUri: Uri?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentIndex by remember { mutableIntStateOf(player.currentMediaItemIndex) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                isPlaying = player.isPlaying
                currentIndex = player.currentMediaItemIndex
                durationMs = player.duration.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    val currentTrack = uiState.tracks.getOrNull(currentIndex)
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = albumTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.track_list_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            uiState.tracks.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = uiState.errorMessage ?: stringResource(id = R.string.player_no_track))
                }
                return@Scaffold
            }
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = currentTrack?.title ?: stringResource(id = R.string.player_no_track),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = currentTrack?.artist ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            AlbumArt(
                albumId = albumId,
                albumArtUri = albumArtUri,
                contentDescription = albumTitle,
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Slider(
                    value = positionMs.coerceAtLeast(0L).toFloat(),
                    onValueChange = { newValue ->
                        positionMs = newValue.toLong().coerceIn(0, durationMs)
                    },
                    onValueChangeFinished = {
                        player.seekTo(positionMs)
                    },
                    valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatDuration(positionMs),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = formatDuration(durationMs),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(
                    onClick = { player.seekToPreviousMediaItem() },
                    enabled = player.hasPreviousMediaItem(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = stringResource(id = R.string.player_prev),
                        modifier = Modifier
                            .width(32.dp)
                            .height(32.dp),
                    )
                }
                Spacer(modifier = Modifier.width(32.dp))
                IconButton(
                    onClick = {
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) {
                            stringResource(id = R.string.player_pause)
                        } else {
                            stringResource(id = R.string.player_play)
                        },
                        modifier = Modifier
                            .width(48.dp)
                            .height(48.dp),
                    )
                }
                Spacer(modifier = Modifier.width(32.dp))
                IconButton(
                    onClick = { player.seekToNextMediaItem() },
                    enabled = player.hasNextMediaItem(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = stringResource(id = R.string.player_next),
                        modifier = Modifier
                            .width(32.dp)
                            .height(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumArt(
    albumId: Long?,
    albumArtUri: Uri?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && albumId != null) {
        val context = LocalContext.current
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
        val bitmap = runCatching {
            context.contentResolver.loadThumbnail(uri, Size(1024, 1024), null)
        }.getOrNull()
        AsyncImage(
            model = bitmap,
            contentDescription = contentDescription,
            modifier = modifier
                .aspectRatio(1f)
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
                .aspectRatio(1f)
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
