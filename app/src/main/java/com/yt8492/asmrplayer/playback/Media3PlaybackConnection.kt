package com.yt8492.asmrplayer.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.yt8492.asmrplayer.service.PlaybackService
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

internal data class PlaybackConnectionState(
    val controller: MediaController? = null,
    val mediaItem: MediaItem? = null,
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val connectionFailed: Boolean = false,
)

/** UI全体で1接続を共有。生成・イベント・解放はMedia3のapplication looperで行う。 */
internal class Media3PlaybackConnection(
    private val future: ListenableFuture<MediaController>,
    executor: Executor,
) : AutoCloseable {
    private val mutableState = MutableStateFlow(PlaybackConnectionState())
    val state = mutableState.asStateFlow()
    private var closed = false
    private var controller: MediaController? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = update()
    }

    init {
        future.addListener({
            if (!closed) {
                try {
                    controller = future.get().also { it.addListener(listener) }
                    update()
                } catch (error: Exception) {
                    Timber.e(error, "再生サービスへの接続に失敗しました")
                    mutableState.value = PlaybackConnectionState(connectionFailed = true)
                }
            }
        }, executor)
    }

    private fun update() {
        val ctl = controller ?: return
        mutableState.value = PlaybackConnectionState(ctl, ctl.currentMediaItem, ctl.currentMediaItemIndex, ctl.isPlaying)
    }

    private fun onDisconnected() {
        if (closed) return
        controller?.removeListener(listener)
        controller = null
        mutableState.value = PlaybackConnectionState(connectionFailed = true)
    }

    override fun close() {
        if (closed) return
        closed = true
        controller?.removeListener(listener)
        controller = null
        // 未接続の場合もMedia3側のholderまで解放する。
        MediaController.releaseFuture(future)
        mutableState.value = PlaybackConnectionState()
    }

    companion object {
        fun connect(context: Context): Media3PlaybackConnection {
            val app = context.applicationContext
            val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            var connection: Media3PlaybackConnection? = null
            val future = MediaController.Builder(app, token).setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) { connection?.onDisconnected() }
            }).buildAsync()
            return Media3PlaybackConnection(future, ContextCompat.getMainExecutor(app)).also { connection = it }
        }
    }
}
