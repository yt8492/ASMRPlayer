package com.yt8492.asmrplayer.ui.player

import android.content.ComponentName
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    queue: PlaybackQueue,
    startTrackId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModel.provideFactory(LocalContext.current, queue, startTrackId),
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
        val queueTitle = queue.title
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
            val trackAlbumTitle = track.albumTitle.ifEmpty { queueTitle }
            MediaItem.Builder()
                .setUri(track.uri)
                .setMediaId(track.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(trackAlbumTitle)
                        .setExtras(
                            Bundle().apply {
                                when (queue) {
                                    is PlaybackQueue.Album -> {
                                        putString(PlaybackService.EXTRA_QUEUE_TYPE, PlaybackService.QUEUE_TYPE_ALBUM)
                                        putLong(PlaybackService.EXTRA_ALBUM_ID, queue.albumId)
                                        putString(PlaybackService.EXTRA_ALBUM_TITLE, queue.albumTitle)
                                        putString(PlaybackService.EXTRA_ALBUM_ART_URI, queue.albumArtUri?.toString().orEmpty())
                                    }

                                    is PlaybackQueue.Playlist -> {
                                        putString(PlaybackService.EXTRA_QUEUE_TYPE, PlaybackService.QUEUE_TYPE_PLAYLIST)
                                        putLong(PlaybackService.EXTRA_PLAYLIST_ID, queue.playlistId)
                                        putString(PlaybackService.EXTRA_PLAYLIST_NAME, queue.playlistName)
                                    }

                                    is PlaybackQueue.Folder -> {
                                        putString(PlaybackService.EXTRA_QUEUE_TYPE, PlaybackService.QUEUE_TYPE_FOLDER)
                                        putString(PlaybackService.EXTRA_FOLDER_PATH, queue.directoryPath)
                                        putString(PlaybackService.EXTRA_FOLDER_TITLE, queue.directoryTitle)
                                    }
                                }
                                putLong(PlaybackService.EXTRA_TRACK_ID, track.id)
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
            queueTitle = queue.title,
            fallbackAlbumArtUri = queue.albumArtUri,
            onBack = onBack,
            onCurrentTrackChanged = viewModel::onCurrentTrackChanged,
            onSaveTrackLoop = viewModel::saveTrackLoop,
            onDeleteTrackLoop = viewModel::deleteTrackLoop,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    player: Player,
    uiState: PlayerUiState,
    queueTitle: String,
    fallbackAlbumArtUri: Uri?,
    onBack: () -> Unit,
    onCurrentTrackChanged: (Long?) -> Unit,
    onSaveTrackLoop: (trackId: Long, startMs: Long, endMs: Long) -> Unit,
    onDeleteTrackLoop: (trackId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentIndex by remember { mutableIntStateOf(player.currentMediaItemIndex) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }
    var seekFeedbackEventId by remember { mutableIntStateOf(0) }
    var loopTrackId by remember { mutableStateOf<Long?>(null) }
    var loopStartMs by remember { mutableStateOf<Long?>(null) }
    var loopEndMs by remember { mutableStateOf<Long?>(null) }
    var isLooping by remember { mutableStateOf(false) }

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
            val currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            val activeRange = TrackLoopRangeFactory.create(loopStartMs, loopEndMs, durationMs)
            if (isLooping && activeRange != null && currentPositionMs >= activeRange.endMs) {
                player.seekTo(activeRange.startMs)
                positionMs = activeRange.startMs
            } else {
                positionMs = currentPositionMs
            }
            delay(500)
        }
    }

    LaunchedEffect(seekFeedbackEventId) {
        if (seekFeedbackEventId > 0) {
            delay(SEEK_FEEDBACK_VISIBLE_MS)
            seekFeedback = null
        }
    }

    val currentTrack = uiState.tracks.getOrNull(currentIndex)
    val currentTrackId = currentTrack?.id

    LaunchedEffect(currentTrackId) {
        onCurrentTrackChanged(currentTrackId)
        loopTrackId = currentTrackId
        loopStartMs = null
        loopEndMs = null
        isLooping = false
    }

    LaunchedEffect(currentTrackId, uiState.currentTrackLoop) {
        val trackLoop = uiState.currentTrackLoop
        if (trackLoop != null && trackLoop.trackId == currentTrackId) {
            loopTrackId = trackLoop.trackId
            loopStartMs = trackLoop.startMs
            loopEndMs = trackLoop.endMs
        } else if (trackLoop == null && loopTrackId == currentTrackId) {
            loopStartMs = null
            loopEndMs = null
        }
        isLooping = false
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = queueTitle) },
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

        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .pointerInput(player) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val isLeftSide = offset.x < size.width / 2f
                            val seekOffsetMs = if (isLeftSide) {
                                seekFeedback = SeekFeedback.Backward
                                -DOUBLE_TAP_SEEK_INTERVAL_MS
                            } else {
                                seekFeedback = SeekFeedback.Forward
                                DOUBLE_TAP_SEEK_INTERVAL_MS
                            }
                            seekFeedbackEventId += 1
                            player.seekRelative(seekOffsetMs)
                            positionMs = player.currentPosition.coerceAtLeast(0L)
                            val activeRange = TrackLoopRangeFactory.create(loopStartMs, loopEndMs, durationMs)
                            if (
                                isLooping &&
                                activeRange != null &&
                                TrackLoopRangeFactory.shouldStopLoopAfterUserSeek(activeRange, positionMs)
                            ) {
                                isLooping = false
                            }
                        },
                    )
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
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
                    albumId = currentTrack?.albumId,
                    albumArtUri = currentTrack?.albumArtUri ?: fallbackAlbumArtUri,
                    contentDescription = currentTrack?.albumTitle ?: queueTitle,
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
                            val activeRange = TrackLoopRangeFactory.create(loopStartMs, loopEndMs, durationMs)
                            if (
                                isLooping &&
                                activeRange != null &&
                                TrackLoopRangeFactory.shouldStopLoopAfterUserSeek(activeRange, positionMs)
                            ) {
                                isLooping = false
                            }
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

                TrackLoopStatusText(
                    startMs = loopStartMs,
                    endMs = loopEndMs,
                    durationMs = durationMs,
                    isLooping = isLooping,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
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
                    ABLoopButton(
                        state = ABLoopButtonState(
                            startMs = loopStartMs,
                            endMs = loopEndMs,
                            isLooping = isLooping,
                        ),
                        enabled = currentTrackId != null,
                        onClick = {
                            val state = ABLoopButtonState(
                                startMs = loopStartMs,
                                endMs = loopEndMs,
                                isLooping = isLooping,
                            )
                            when (
                                val action = ABLoopButtonStateMachine.onClick(
                                    state = state,
                                    positionMs = positionMs,
                                    durationMs = durationMs,
                                )
                            ) {
                                is ABLoopButtonAction.SetStart -> {
                                    loopTrackId = currentTrackId
                                    loopStartMs = action.startMs
                                    loopEndMs = null
                                    isLooping = false
                                }

                                is ABLoopButtonAction.SetEndAndStartLoop -> {
                                    val trackId = currentTrackId ?: return@ABLoopButton
                                    loopTrackId = trackId
                                    loopStartMs = action.range.startMs
                                    loopEndMs = action.range.endMs
                                    onSaveTrackLoop(trackId, action.range.startMs, action.range.endMs)
                                    player.seekTo(action.range.startMs)
                                    positionMs = action.range.startMs
                                    isLooping = true
                                }

                                is ABLoopButtonAction.StartLoop -> {
                                    player.seekTo(action.range.startMs)
                                    positionMs = action.range.startMs
                                    isLooping = true
                                }

                                ABLoopButtonAction.StopLoop -> {
                                    isLooping = false
                                }

                                ABLoopButtonAction.Clear,
                                ABLoopButtonAction.None,
                                -> Unit
                            }
                        },
                        onLongClick = {
                            if (ABLoopButtonStateMachine.onLongClick() == ABLoopButtonAction.Clear) {
                                currentTrackId?.let(onDeleteTrackLoop)
                                loopStartMs = null
                                loopEndMs = null
                                isLooping = false
                            }
                        },
                    )
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

            seekFeedback?.let { feedback ->
                SeekFeedbackBadge(
                    feedback = feedback,
                    modifier = Modifier
                        .align(feedback.alignment)
                        .padding(horizontal = 32.dp),
                )
            }
        }
    }
}

@Composable
private fun TrackLoopStatusText(
    startMs: Long?,
    endMs: Long?,
    durationMs: Long,
    isLooping: Boolean,
    modifier: Modifier = Modifier,
) {
    val loopRange = TrackLoopRangeFactory.create(startMs, endMs, durationMs)
    val text = when {
        isLooping && loopRange != null -> stringResource(
            id = R.string.player_loop_active_range,
            formatDuration(loopRange.startMs),
            formatDuration(loopRange.endMs),
        )

        loopRange != null -> stringResource(
            id = R.string.player_loop_range,
            formatDuration(loopRange.startMs),
            formatDuration(loopRange.endMs),
        )

        startMs != null -> stringResource(id = R.string.player_loop_start_point, formatDuration(startMs))
        else -> stringResource(id = R.string.player_loop_not_set)
    }
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ABLoopButton(
    state: ABLoopButtonState,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loopRange = TrackLoopRangeFactory.create(state.startMs, state.endMs, Long.MAX_VALUE)
    val clickLabel = when {
        state.isLooping -> stringResource(id = R.string.player_loop_stop)
        loopRange != null -> stringResource(id = R.string.player_loop_start)
        state.startMs != null -> stringResource(id = R.string.player_loop_set_end_and_start)
        else -> stringResource(id = R.string.player_loop_set_start)
    }
    val containerColor = if (state.isLooping) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (state.isLooping) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(containerColor)
            .semantics { contentDescription = clickLabel }
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = clickLabel,
                onLongClickLabel = stringResource(id = R.string.player_loop_clear),
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(id = R.string.player_loop_button_label),
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun SeekFeedbackBadge(
    feedback: SeekFeedback,
    modifier: Modifier = Modifier,
) {
    Text(
        text = feedback.label,
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.large,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        style = MaterialTheme.typography.titleLarge,
    )
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

private fun Player.seekRelative(offsetMs: Long) {
    val currentPositionMs = currentPosition.coerceAtLeast(0L)
    val targetPositionMs = currentPositionMs + offsetMs
    val durationMs = duration
    val clampedPositionMs = if (durationMs > 0) {
        targetPositionMs.coerceIn(0L, durationMs)
    } else {
        targetPositionMs.coerceAtLeast(0L)
    }
    seekTo(clampedPositionMs)
}

private const val DOUBLE_TAP_SEEK_INTERVAL_MS = 10_000L
private const val SEEK_FEEDBACK_VISIBLE_MS = 600L

private enum class SeekFeedback(
    val label: String,
    val alignment: Alignment,
) {
    Backward("-10秒", Alignment.CenterStart),
    Forward("+10秒", Alignment.CenterEnd),
}

private val PlaybackQueue.title: String
    get() = when (this) {
        is PlaybackQueue.Album -> albumTitle
        is PlaybackQueue.Folder -> directoryTitle
        is PlaybackQueue.Playlist -> playlistName
    }

private val PlaybackQueue.albumArtUri: Uri?
    get() = when (this) {
        is PlaybackQueue.Album -> albumArtUri
        is PlaybackQueue.Folder -> null
        is PlaybackQueue.Playlist -> null
    }
