package com.yt8492.asmrplayer.ui.player

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.yt8492.asmrplayer.R
import com.yt8492.asmrplayer.playback.loop.ABLoopButtonState
import com.yt8492.asmrplayer.playback.loop.TrackLoopRangeFactory
import com.yt8492.asmrplayer.ui.common.SingleLineMarqueeText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    player: Player,
    uiState: PlayerUiState,
    queueTitle: String,
    queueArtworkLabel: String?,
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
    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }
    var seekFeedbackEventId by remember { mutableIntStateOf(0) }
    val playback = rememberPlayerScreenState(player)
    val bottomSheetScaffoldState = rememberBottomSheetScaffoldState()
    val coroutineScope = rememberCoroutineScope()
    val shouldApplySheetTopPadding = bottomSheetScaffoldState.bottomSheetState.currentValue == SheetValue.Expanded ||
        bottomSheetScaffoldState.bottomSheetState.targetValue == SheetValue.Expanded
    val expandedSheetTopPadding = if (shouldApplySheetTopPadding) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    } else {
        0.dp
    }

    LaunchedEffect(seekFeedbackEventId) {
        if (seekFeedbackEventId > 0) {
            delay(SEEK_FEEDBACK_VISIBLE_MS)
            seekFeedback = null
        }
    }

    val currentTrack = uiState.queueItems.getOrNull(playback.currentIndex)?.track
    val currentTrackId = currentTrack?.id
    var artworkPickerTarget by remember { mutableStateOf<ArtworkPickerTarget?>(null) }
    var artworkMenuExpanded by remember { mutableStateOf(false) }
    val artworkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { imageUri ->
        val target = artworkPickerTarget ?: return@rememberLauncherForActivityResult
        if (imageUri == null) return@rememberLauncherForActivityResult
        when (target) {
            ArtworkPickerTarget.Queue -> onSaveQueueArtwork(imageUri)
            is ArtworkPickerTarget.Track -> onSaveTrackArtwork(target.trackId, imageUri)
        }
    }

    LaunchedEffect(currentTrackId) { onCurrentTrackChanged(currentTrackId) }
    BindTrackLoop(playback, currentTrackId, uiState.currentTrackLoop)

    BottomSheetScaffold(
        modifier = modifier,
        scaffoldState = bottomSheetScaffoldState,
        containerColor = MaterialTheme.colorScheme.background,
        sheetPeekHeight = if (uiState.queueItems.isEmpty()) 0.dp else PlayerQueueSheetPeekHeight,
        sheetDragHandle = null,
        sheetContent = {
            PlaybackQueueSheet(
                queueItems = uiState.queueItems,
                currentIndex = playback.currentIndex,
                canChangeQueue = player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS),
                topPadding = expandedSheetTopPadding,
                onQueueItemClick = { index ->
                    if (index != playback.currentIndex && index in uiState.queueItems.indices) {
                        player.seekTo(index, 0)
                        player.play()
                        playback.currentIndex = player.currentMediaItemIndex
                        playback.positionMs = player.currentPosition.coerceAtLeast(0L)
                    }
                    coroutineScope.launch {
                        bottomSheetScaffoldState.bottomSheetState.partialExpand()
                    }
                },
                onMoveQueueItem = { fromIndex, toIndex ->
                    if (player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) {
                        player.moveMediaItem(fromIndex, toIndex)
                        playback.currentIndex = player.currentMediaItemIndex
                        onMoveQueueItem(fromIndex, toIndex)
                    }
                },
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    SingleLineMarqueeText(text = queueTitle)
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
                return@BottomSheetScaffold
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
                return@BottomSheetScaffold
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
                            playback.positionMs = player.currentPosition.coerceAtLeast(0L)
                            val activeRange = TrackLoopRangeFactory.create(playback.loopStartMs, playback.loopEndMs, playback.durationMs)
                            if (
                                playback.isLooping &&
                                activeRange != null &&
                                TrackLoopRangeFactory.shouldStopLoopAfterUserSeek(activeRange, playback.positionMs)
                            ) {
                                playback.isLooping = false
                            }
                        },
                    )
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 24.dp,
                        top = 18.dp,
                        end = 24.dp,
                        bottom = PlayerFixedSeekPanelHeight + 8.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val customArtworkUri = uiState.currentTrackArtworkUri ?: uiState.queueArtworkUri
                AlbumArt(
                    albumArtUri = customArtworkUri ?: currentTrack?.albumArtUri,
                    contentDescription = currentTrack?.albumTitle ?: queueTitle,
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SingleLineMarqueeText(
                        text = currentTrack?.title ?: stringResource(id = R.string.player_no_track),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    SingleLineMarqueeText(
                        text = currentTrack?.artist ?: queueTitle,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                TrackLoopStatusText(
                    startMs = playback.loopStartMs,
                    endMs = playback.loopEndMs,
                    durationMs = playback.durationMs,
                    isLooping = playback.isLooping,
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
                            startMs = playback.loopStartMs,
                            endMs = playback.loopEndMs,
                            isLooping = playback.isLooping,
                        ),
                        enabled = currentTrackId != null,
                        onClick = { playback.onLoopClick(currentTrackId, player, onSaveTrackLoop) },
                        onLongClick = { playback.clearLoop(currentTrackId, onDeleteTrackLoop) },
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
                            imageVector = if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playback.isPlaying) {
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
                        repeatMode = playback.repeatMode,
                        onClick = {
                            val nextRepeatMode = playback.repeatMode.nextRepeatMode()
                            player.repeatMode = nextRepeatMode
                            playback.repeatMode = nextRepeatMode
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

            FixedSeekPanel(
                positionMs = playback.positionMs,
                durationMs = playback.durationMs,
                loopStartMs = playback.loopStartMs,
                loopEndMs = playback.loopEndMs,
                onValueChange = { newValue ->
                    playback.positionMs = newValue.toLong().coerceIn(0, playback.durationMs)
                },
                onValueChangeFinished = {
                    player.seekTo(playback.positionMs)
                    val activeRange = TrackLoopRangeFactory.create(playback.loopStartMs, playback.loopEndMs, playback.durationMs)
                    if (
                        playback.isLooping &&
                        activeRange != null &&
                        TrackLoopRangeFactory.shouldStopLoopAfterUserSeek(activeRange, playback.positionMs)
                    ) {
                        playback.isLooping = false
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

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
