package com.yt8492.asmrplayer.ui.player

import android.content.ComponentName
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    startPlaylistTrackId: Long? = null,
    startIndexHint: Int? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModel.provideFactory(
            context = LocalContext.current,
            queue = queue,
            startTrackId = startTrackId,
            startPlaylistTrackId = startPlaylistTrackId,
            startIndexHint = startIndexHint,
        ),
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
    var isInitialQueuePrepared by rememberSaveable { mutableStateOf(false) }

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

    LaunchedEffect(controller, uiState.queueItems, uiState.startIndex, isInitialQueuePrepared) {
        val ctl = controller ?: return@LaunchedEffect
        if (uiState.queueItems.isEmpty()) return@LaunchedEffect
        val shouldPrepareInitialQueue = !isInitialQueuePrepared || ctl.mediaItemCount == 0
        if (!shouldPrepareInitialQueue) return@LaunchedEffect
        val queueTitle = queue.title
        val mediaIds = uiState.queueItems.map { it.track.id.toString() }
        val currentMediaIds = List(ctl.mediaItemCount) { index ->
            ctl.getMediaItemAt(index).mediaId
        }
        val startIndex = uiState.startIndex.takeIf { it in uiState.queueItems.indices } ?: 0
        if (currentMediaIds == mediaIds) {
            if (ctl.currentMediaItem?.mediaId != startTrackId.toString() || ctl.currentMediaItemIndex != startIndex) {
                ctl.seekTo(startIndex, 0)
                ctl.play()
            }
            isInitialQueuePrepared = true
            return@LaunchedEffect
        }

        val mediaItems = uiState.queueItems.map { queueItem ->
            val track = queueItem.track
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
        isInitialQueuePrepared = true
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
            queueArtworkLabel = queue.artworkLabel(),
            fallbackAlbumArtUri = queue.albumArtUri,
            onBack = onBack,
            onCurrentTrackChanged = viewModel::onCurrentTrackChanged,
            onSaveTrackLoop = viewModel::saveTrackLoop,
            onDeleteTrackLoop = viewModel::deleteTrackLoop,
            onSaveTrackArtwork = viewModel::saveTrackArtwork,
            onDeleteTrackArtwork = viewModel::deleteTrackArtwork,
            onSaveQueueArtwork = viewModel::saveQueueArtwork,
            onDeleteQueueArtwork = viewModel::deleteQueueArtwork,
            onMoveQueueItem = viewModel::moveQueueItem,
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
    queueArtworkLabel: String?,
    fallbackAlbumArtUri: Uri?,
    onBack: () -> Unit,
    onCurrentTrackChanged: (Long?) -> Unit,
    onSaveTrackLoop: (trackId: Long, startMs: Long, endMs: Long) -> Unit,
    onDeleteTrackLoop: (trackId: Long) -> Unit,
    onSaveTrackArtwork: (trackId: Long, imageUri: Uri) -> Unit,
    onDeleteTrackArtwork: (trackId: Long) -> Unit,
    onSaveQueueArtwork: (imageUri: Uri) -> Unit,
    onDeleteQueueArtwork: () -> Unit,
    onMoveQueueItem: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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
    var repeatMode by remember { mutableIntStateOf(player.repeatMode) }
    var isQueueSheetVisible by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                isPlaying = player.isPlaying
                currentIndex = player.currentMediaItemIndex
                durationMs = player.duration.coerceAtLeast(0L)
                repeatMode = player.repeatMode
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

    val currentTrack = uiState.queueItems.getOrNull(currentIndex)?.track
    val currentTrackId = currentTrack?.id
    var artworkPickerTarget by remember { mutableStateOf<ArtworkPickerTarget?>(null) }
    var artworkMenuExpanded by remember { mutableStateOf(false) }
    val artworkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { imageUri ->
        val target = artworkPickerTarget ?: return@rememberLauncherForActivityResult
        if (imageUri == null) return@rememberLauncherForActivityResult
        val permissionTaken = runCatching {
            context.contentResolver.takePersistableUriPermission(
                imageUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.isSuccess
        if (!permissionTaken) return@rememberLauncherForActivityResult
        when (target) {
            ArtworkPickerTarget.Queue -> onSaveQueueArtwork(imageUri)
            is ArtworkPickerTarget.Track -> onSaveTrackArtwork(target.trackId, imageUri)
        }
    }

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
        containerColor = MaterialTheme.colorScheme.background,
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
                actions = {
                    IconButton(
                        onClick = { isQueueSheetVisible = true },
                        enabled = uiState.queueItems.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = stringResource(id = R.string.player_queue),
                        )
                    }
                    currentTrackId?.let { trackId ->
                        IconButton(
                            onClick = { artworkMenuExpanded = true },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = stringResource(id = R.string.player_artwork_menu),
                            )
                        }
                        DropdownMenu(
                            expanded = artworkMenuExpanded,
                            onDismissRequest = { artworkMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(
                                            id = if (uiState.currentTrackArtworkUri == null) {
                                                R.string.player_track_artwork_select
                                            } else {
                                                R.string.player_track_artwork_change
                                            },
                                        ),
                                    )
                                },
                                onClick = {
                                    artworkMenuExpanded = false
                                    artworkPickerTarget = ArtworkPickerTarget.Track(trackId)
                                    artworkPicker.launch(arrayOf("image/*"))
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Image,
                                        contentDescription = null,
                                    )
                                },
                            )
                            queueArtworkLabel?.let { label ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(
                                                id = if (uiState.queueArtworkUri == null) {
                                                    R.string.player_queue_artwork_select
                                                } else {
                                                    R.string.player_queue_artwork_change
                                                },
                                                label,
                                            ),
                                        )
                                    },
                                    onClick = {
                                        artworkMenuExpanded = false
                                        artworkPickerTarget = ArtworkPickerTarget.Queue
                                        artworkPicker.launch(arrayOf("image/*"))
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Image,
                                            contentDescription = null,
                                        )
                                    },
                                )
                            }
                            if (uiState.currentTrackArtworkUri != null) {
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(id = R.string.player_track_artwork_clear)) },
                                    onClick = {
                                        artworkMenuExpanded = false
                                        onDeleteTrackArtwork(trackId)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = null,
                                        )
                                    },
                                )
                            }
                            if (queueArtworkLabel != null && uiState.queueArtworkUri != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(
                                                id = R.string.player_queue_artwork_clear,
                                                queueArtworkLabel,
                                            ),
                                        )
                                    },
                                    onClick = {
                                        artworkMenuExpanded = false
                                        onDeleteQueueArtwork()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = null,
                                        )
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
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

            uiState.queueItems.isEmpty() -> {
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
                .background(MaterialTheme.colorScheme.background)
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
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val customArtworkUri = uiState.currentTrackArtworkUri ?: uiState.queueArtworkUri
                AlbumArt(
                    albumArtUri = customArtworkUri ?: currentTrack?.albumArtUri ?: fallbackAlbumArtUri,
                    contentDescription = currentTrack?.albumTitle ?: queueTitle,
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = currentTrack?.title ?: stringResource(id = R.string.player_no_track),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = currentTrack?.artist ?: queueTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ABLoopSlider(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        startMs = loopStartMs,
                        endMs = loopEndMs,
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
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatDuration(positionMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatDuration(durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
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
                        modifier = Modifier
                            .size(88.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
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
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    RepeatModeButton(
                        repeatMode = repeatMode,
                        onClick = {
                            val nextRepeatMode = repeatMode.nextRepeatMode()
                            player.repeatMode = nextRepeatMode
                            repeatMode = nextRepeatMode
                        },
                    )
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

    if (isQueueSheetVisible) {
        PlaybackQueueSheet(
            queueItems = uiState.queueItems,
            currentIndex = currentIndex,
            canChangeQueue = player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS),
            onDismiss = { isQueueSheetVisible = false },
            onQueueItemClick = { index ->
                if (index != currentIndex && index in uiState.queueItems.indices) {
                    player.seekTo(index, 0)
                    player.play()
                    currentIndex = player.currentMediaItemIndex
                    positionMs = player.currentPosition.coerceAtLeast(0L)
                }
                isQueueSheetVisible = false
            },
            onMoveQueueItem = { fromIndex, toIndex ->
                if (player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) {
                    player.moveMediaItem(fromIndex, toIndex)
                    currentIndex = player.currentMediaItemIndex
                    onMoveQueueItem(fromIndex, toIndex)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackQueueSheet(
    queueItems: List<PlayerQueueItem>,
    currentIndex: Int,
    canChangeQueue: Boolean,
    onDismiss: () -> Unit,
    onQueueItemClick: (Int) -> Unit,
    onMoveQueueItem: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(id = R.string.player_queue),
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(id = R.string.player_queue_count, queueItems.size),
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(
                    items = queueItems,
                    key = { _, queueItem -> queueItem.queueItemId },
                ) { index, queueItem ->
                    PlaybackQueueListItem(
                        queueItem = queueItem,
                        index = index,
                        isCurrent = index == currentIndex,
                        canChangeQueue = canChangeQueue,
                        onClick = { onQueueItemClick(index) },
                        onMoveQueueItem = onMoveQueueItem,
                        lastIndex = queueItems.lastIndex,
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PlaybackQueueListItem(
    queueItem: PlayerQueueItem,
    index: Int,
    isCurrent: Boolean,
    canChangeQueue: Boolean,
    onClick: () -> Unit,
    onMoveQueueItem: (fromIndex: Int, toIndex: Int) -> Unit,
    lastIndex: Int,
    modifier: Modifier = Modifier,
) {
    var currentIndex by remember(queueItem.queueItemId) { mutableIntStateOf(index) }
    var draggingQueueItemId by remember { mutableStateOf<Long?>(null) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }
    val latestLastIndex by rememberUpdatedState(lastIndex)
    val itemHeightPx = with(LocalDensity.current) { 72.dp.toPx() }
    val track = queueItem.track

    ListItem(
        modifier = modifier
            .graphicsLayer {
                translationY = if (draggingQueueItemId == queueItem.queueItemId) {
                    draggingOffset
                } else {
                    0f
                }
            }
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            headlineColor = if (isCurrent) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            supportingColor = if (isCurrent) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        leadingContent = {
            if (canChangeQueue) {
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = stringResource(id = R.string.player_queue_reorder),
                    modifier = Modifier.pointerInput(queueItem.queueItemId) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingQueueItemId = queueItem.queueItemId
                                draggingOffset = 0f
                                currentIndex = index
                            },
                            onDragEnd = {
                                draggingQueueItemId = null
                                draggingOffset = 0f
                            },
                            onDragCancel = {
                                draggingQueueItemId = null
                                draggingOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                draggingOffset += dragAmount.y
                                while (draggingOffset > itemHeightPx && currentIndex < latestLastIndex) {
                                    onMoveQueueItem(currentIndex, currentIndex + 1)
                                    currentIndex += 1
                                    draggingOffset -= itemHeightPx
                                }
                                while (draggingOffset < -itemHeightPx && currentIndex > 0) {
                                    onMoveQueueItem(currentIndex, currentIndex - 1)
                                    currentIndex -= 1
                                    draggingOffset += itemHeightPx
                                }
                            },
                        )
                    },
                )
            }
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
            Text(
                text = if (isCurrent) {
                    stringResource(id = R.string.player_queue_current)
                } else {
                    formatDuration(track.durationMs)
                },
                style = MaterialTheme.typography.labelMedium,
            )
        },
    )
}

@Composable
private fun RepeatModeButton(
    repeatMode: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = repeatMode != Player.REPEAT_MODE_OFF
    val contentDescription = when (repeatMode) {
        Player.REPEAT_MODE_ONE -> stringResource(id = R.string.player_repeat_one)
        Player.REPEAT_MODE_ALL -> stringResource(id = R.string.player_repeat_all)
        else -> stringResource(id = R.string.player_repeat_off)
    }
    val tint = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    IconButton(
        onClick = onClick,
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        },
    ) {
        Icon(
            imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) {
                Icons.Filled.RepeatOne
            } else {
                Icons.Filled.Repeat
            },
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .width(32.dp)
                .height(32.dp),
        )
    }
}

@Composable
private fun ABLoopSlider(
    positionMs: Long,
    durationMs: Long,
    startMs: Long?,
    endMs: Long?,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val startMarkerColor = MaterialTheme.colorScheme.primary
    val endMarkerColor = MaterialTheme.colorScheme.tertiary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value = positionMs.coerceAtLeast(0L).toFloat(),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawABLoopMarker(
                label = "A",
                positionMs = startMs,
                durationMs = durationMs,
                color = startMarkerColor,
            )
            drawABLoopMarker(
                label = "B",
                positionMs = endMs,
                durationMs = durationMs,
                color = endMarkerColor,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawABLoopMarker(
    label: String,
    positionMs: Long?,
    durationMs: Long,
    color: Color,
) {
    if (positionMs == null || durationMs <= 0L) return
    val fraction = positionMs.coerceIn(0L, durationMs).toFloat() / durationMs.toFloat()
    val x = size.width * fraction
    val markerTop = size.height * 0.18f
    val markerBottom = size.height * 0.82f
    drawLine(
        color = color,
        start = Offset(x = x, y = markerTop),
        end = Offset(x = x, y = markerBottom),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round,
    )

    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11.dp.toPx()
        this.color = color.toArgb()
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    drawContext.canvas.nativeCanvas.drawText(
        label,
        x.coerceIn(10.dp.toPx(), size.width - 10.dp.toPx()),
        12.dp.toPx(),
        labelPaint,
    )
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
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
    albumArtUri: Uri?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = albumArtUri,
        contentDescription = contentDescription,
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.large),
        contentScale = ContentScale.Fit,
        placeholder = rememberVectorPainter(Icons.Filled.Album),
        error = rememberVectorPainter(Icons.Filled.Album),
        fallback = rememberVectorPainter(Icons.Filled.Album),
    )
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

private fun Int.nextRepeatMode(): Int = when (this) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
    else -> Player.REPEAT_MODE_OFF
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

@Composable
private fun PlaybackQueue.artworkLabel(): String? {
    return when (this) {
        is PlaybackQueue.Album -> stringResource(id = R.string.player_artwork_scope_album)
        is PlaybackQueue.Playlist -> stringResource(id = R.string.player_artwork_scope_playlist)
        is PlaybackQueue.Folder -> stringResource(id = R.string.player_artwork_scope_folder)
    }
}

private sealed interface ArtworkPickerTarget {
    data class Track(val trackId: Long) : ArtworkPickerTarget
    data object Queue : ArtworkPickerTarget
}
