package com.yt8492.asmrplayer.ui.player

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yt8492.asmrplayer.R
import com.yt8492.asmrplayer.di.appContainer
import com.yt8492.asmrplayer.di.playerFactory
import com.yt8492.asmrplayer.playback.model.PlaybackQueue
import com.yt8492.asmrplayer.playback.model.title
import com.yt8492.asmrplayer.playback.preparePlaybackQueue
import com.yt8492.asmrplayer.ui.common.rememberPlaybackConnectionState

@Composable
fun PlayerRoute(
    queue: PlaybackQueue,
    startTrackId: Long,
    startPlaylistTrackId: Long? = null,
    startIndexHint: Int? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel(
        factory = LocalContext.current.appContainer().playerFactory(
            queue = queue,
            startTrackId = startTrackId,
            startPlaylistTrackId = startPlaylistTrackId,
            startIndexHint = startIndexHint,
        ),
    ),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val connection by rememberPlaybackConnectionState()
    val controller = connection.controller
    val controllerError = connection.connectionFailed
    var isInitialQueuePrepared by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        if (uiState.queueItems.isNotEmpty()) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeError()
        }
    }

    LaunchedEffect(controller, uiState.queueItems, uiState.startIndex, isInitialQueuePrepared) {
        val ctl = controller ?: return@LaunchedEffect
        if (uiState.queueItems.isEmpty()) return@LaunchedEffect
        val shouldPrepareInitialQueue = !isInitialQueuePrepared || ctl.mediaItemCount == 0
        if (!shouldPrepareInitialQueue) return@LaunchedEffect
        ctl.preparePlaybackQueue(queue, uiState.queueItems.map { it.track }, startTrackId, uiState.startIndex)
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
