package com.yt8492.asmrplayer.ui.common

import android.content.ComponentName
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.yt8492.asmrplayer.R
import com.yt8492.asmrplayer.data.local.AppDatabase
import com.yt8492.asmrplayer.data.repository.QueueArtworkRepository
import com.yt8492.asmrplayer.data.repository.QueueArtworkRepositoryImpl
import com.yt8492.asmrplayer.data.repository.TrackArtworkRepository
import com.yt8492.asmrplayer.data.repository.TrackArtworkRepositoryImpl
import com.yt8492.asmrplayer.navigation.PlaybackDestination
import com.yt8492.asmrplayer.service.PlaybackService
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MiniPlaybackController(
    onOpenPlayer: (PlaybackDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playbackState by rememberMiniPlaybackState()
    val state = playbackState ?: return
    val artworkUri by rememberMiniPlaybackArtworkUri(state)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MiniPlaybackControllerHeight)
                .clickable { onOpenPlayer(state.destination) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = artworkUri,
                contentDescription = state.title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
                placeholder = rememberVectorPainter(Icons.Filled.MusicNote),
                error = rememberVectorPainter(Icons.Filled.MusicNote),
                fallback = rememberVectorPainter(Icons.Filled.MusicNote),
            )
            Text(
                text = state.title,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = {
                    if (state.isPlaying) {
                        state.controller.pause()
                    } else {
                        state.controller.play()
                    }
                },
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) {
                        stringResource(id = R.string.player_pause)
                    } else {
                        stringResource(id = R.string.player_play)
                    },
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun rememberMiniPlaybackState(): State<MiniPlaybackState?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<MiniPlaybackState?>(null) }
    val sessionToken = remember {
        SessionToken(context, ComponentName(context, PlaybackService::class.java))
    }
    val controllerFuture = remember {
        MediaController.Builder(context, sessionToken).buildAsync()
    }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    DisposableEffect(controllerFuture) {
        var controller: MediaController? = null
        var listener: Player.Listener? = null
        controllerFuture.addListener(
            {
                runCatching {
                    controllerFuture.get()
                }.onSuccess { mediaController ->
                    controller = mediaController
                    fun updateState() {
                        state.value = mediaController.toMiniPlaybackState()
                    }
                    updateState()
                    listener = object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            updateState()
                        }
                    }.also(mediaController::addListener)
                }.onFailure {
                    state.value = null
                }
            },
            mainExecutor,
        )
        onDispose {
            listener?.let { controller?.removeListener(it) }
            controllerFuture.cancel(true)
            controller?.release()
        }
    }

    return state
}

@Composable
private fun rememberMiniPlaybackArtworkUri(state: MiniPlaybackState): State<Uri?> {
    val context = LocalContext.current
    val database = remember { AppDatabase.getInstance(context.applicationContext) }
    val trackArtworkRepository = remember(database) {
        TrackArtworkRepositoryImpl(database.trackArtworkDao())
    }
    val queueArtworkRepository = remember(database) {
        QueueArtworkRepositoryImpl(database.queueArtworkDao())
    }
    val artworkUri = remember { mutableStateOf(state.metadataArtworkUri) }

    LaunchedEffect(
        state.trackId,
        state.queueType,
        state.queueKey,
        state.metadataArtworkUri,
        trackArtworkRepository,
        queueArtworkRepository,
    ) {
        artworkUri.value = resolveInitialArtworkUri(
            trackArtworkRepository = trackArtworkRepository,
            queueArtworkRepository = queueArtworkRepository,
            state = state,
        )
    }

    LaunchedEffect(state.trackId, state.metadataArtworkUri, trackArtworkRepository) {
        trackArtworkRepository.observeTrackArtwork(state.trackId).collectLatest { trackArtwork ->
            artworkUri.value = trackArtwork?.imageUri ?: resolveInitialArtworkUri(
                trackArtworkRepository = trackArtworkRepository,
                queueArtworkRepository = queueArtworkRepository,
                state = state,
            )
        }
    }

    LaunchedEffect(state.queueType, state.queueKey, state.metadataArtworkUri, queueArtworkRepository) {
        val queueType = state.queueType ?: return@LaunchedEffect
        val queueKey = state.queueKey ?: return@LaunchedEffect
        queueArtworkRepository.observeQueueArtwork(queueType, queueKey).collectLatest {
            artworkUri.value = resolveInitialArtworkUri(
                trackArtworkRepository = trackArtworkRepository,
                queueArtworkRepository = queueArtworkRepository,
                state = state,
            )
        }
    }

    return artworkUri
}

private suspend fun resolveInitialArtworkUri(
    trackArtworkRepository: TrackArtworkRepository,
    queueArtworkRepository: QueueArtworkRepository,
    state: MiniPlaybackState,
): Uri? {
    val trackArtworkUri = trackArtworkRepository.getTrackArtwork(state.trackId)?.imageUri
    if (trackArtworkUri != null) return trackArtworkUri
    val queueType = state.queueType
    val queueKey = state.queueKey
    if (queueType != null && queueKey != null) {
        val queueArtworkUri = queueArtworkRepository.getQueueArtwork(queueType, queueKey)?.imageUri
        if (queueArtworkUri != null) return queueArtworkUri
    }
    return state.metadataArtworkUri
}

private fun MediaController.toMiniPlaybackState(): MiniPlaybackState? {
    val mediaItem = currentMediaItem ?: return null
    val trackId = mediaItem.mediaId.toLongOrNull() ?: return null
    val destination = mediaItem.toPlaybackDestination(currentMediaItemIndex) ?: return null
    val title = mediaItem.mediaMetadata.title?.toString()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val extras = mediaItem.mediaMetadata.extras
    val queueType = extras?.getString(PlaybackService.EXTRA_QUEUE_TYPE)
    return MiniPlaybackState(
        controller = this,
        title = title,
        isPlaying = isPlaying,
        trackId = trackId,
        metadataArtworkUri = mediaItem.mediaMetadata.artworkUri,
        queueType = queueType,
        queueKey = when (queueType) {
            PlaybackService.QUEUE_TYPE_ALBUM -> extras.getLong(PlaybackService.EXTRA_ALBUM_ID, -1L)
                .takeIf { it >= 0 }
                ?.toString()

            PlaybackService.QUEUE_TYPE_PLAYLIST -> extras.getLong(PlaybackService.EXTRA_PLAYLIST_ID, -1L)
                .takeIf { it >= 0 }
                ?.toString()

            PlaybackService.QUEUE_TYPE_FOLDER -> extras.getString(PlaybackService.EXTRA_FOLDER_PATH)
                ?.takeIf { it.isNotEmpty() }

            else -> null
        },
        destination = destination,
    )
}

private fun MediaItem.toPlaybackDestination(currentIndex: Int): PlaybackDestination? {
    val extras = mediaMetadata.extras
    val queueType = extras?.getString(PlaybackService.EXTRA_QUEUE_TYPE)
        ?.takeIf { it.isNotEmpty() }
        ?: PlaybackService.QUEUE_TYPE_ALBUM
    val trackId = mediaId.toLongOrNull() ?: return null
    val albumId = extras?.getLong(PlaybackService.EXTRA_ALBUM_ID, -1L) ?: -1L
    val playlistId = extras?.getLong(PlaybackService.EXTRA_PLAYLIST_ID, -1L) ?: -1L
    if (queueType == PlaybackService.QUEUE_TYPE_ALBUM && albumId < 0) return null
    if (queueType == PlaybackService.QUEUE_TYPE_PLAYLIST && playlistId < 0) return null
    return PlaybackDestination(
        queueType = queueType,
        albumId = albumId,
        playlistId = playlistId,
        trackId = trackId,
        startIndex = currentIndex.takeIf { it >= 0 },
        albumTitle = mediaMetadata.albumTitle?.toString().orEmpty(),
        albumArtUri = extras?.getString(PlaybackService.EXTRA_ALBUM_ART_URI)
            ?.takeIf { it.isNotEmpty() }
            ?.let { Uri.parse(it) },
        playlistName = extras?.getString(PlaybackService.EXTRA_PLAYLIST_NAME).orEmpty(),
        folderPath = extras?.getString(PlaybackService.EXTRA_FOLDER_PATH).orEmpty(),
        folderTitle = extras?.getString(PlaybackService.EXTRA_FOLDER_TITLE).orEmpty(),
        requestId = SystemClock.elapsedRealtime(),
    )
}

private data class MiniPlaybackState(
    val controller: MediaController,
    val title: String,
    val isPlaying: Boolean,
    val trackId: Long,
    val metadataArtworkUri: Uri?,
    val queueType: String?,
    val queueKey: String?,
    val destination: PlaybackDestination,
)

private val MiniPlaybackControllerHeight = 72.dp
