package com.yt8492.asmrplayer.ui.common

import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.yt8492.asmrplayer.service.PlaybackService

@Composable
fun rememberCurrentPlaybackTrackId(): State<Long?> {
    val context = LocalContext.current
    val currentTrackId = remember { mutableStateOf<Long?>(null) }
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
                    fun updateCurrentTrackId() {
                        currentTrackId.value = mediaController.currentMediaItem?.mediaId?.toLongOrNull()
                    }
                    updateCurrentTrackId()
                    listener = object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            updateCurrentTrackId()
                        }
                    }.also(mediaController::addListener)
                }.onFailure {
                    currentTrackId.value = null
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

    return currentTrackId
}
