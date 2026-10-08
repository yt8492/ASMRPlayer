package com.yt8492.asmrplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import com.yt8492.asmrplayer.data.model.TrackLoop
import com.yt8492.asmrplayer.playback.loop.TrackLoopRangeFactory
import com.yt8492.asmrplayer.playback.loop.ABLoopButtonState
import com.yt8492.asmrplayer.playback.loop.ABLoopButtonStateMachine
import com.yt8492.asmrplayer.playback.loop.ABLoopButtonAction
import kotlinx.coroutines.delay

/** 再生画面に閉じた状態。画面を離れるとABリピートの監視も終了する。 */
internal class PlayerScreenState(player: Player) {
    var isPlaying by mutableStateOf(player.isPlaying)
    var currentIndex by mutableIntStateOf(player.currentMediaItemIndex)
    var positionMs by mutableLongStateOf(0)
    var durationMs by mutableLongStateOf(0)
    var repeatMode by mutableIntStateOf(player.repeatMode)
    var loopTrackId by mutableStateOf<Long?>(null)
    var loopStartMs by mutableStateOf<Long?>(null)
    var loopEndMs by mutableStateOf<Long?>(null)
    var isLooping by mutableStateOf(false)

    fun update(player: Player) {
        isPlaying = player.isPlaying
        currentIndex = player.currentMediaItemIndex
        durationMs = player.duration.coerceAtLeast(0)
        repeatMode = player.repeatMode
    }

    fun tick(player: Player) {
        val position = player.currentPosition.coerceAtLeast(0)
        durationMs = player.duration.coerceAtLeast(0)
        val range = TrackLoopRangeFactory.create(loopStartMs, loopEndMs, durationMs)
        if (isLooping && range != null && position >= range.endMs) {
            player.seekTo(range.startMs)
            positionMs = range.startMs
        } else {
            positionMs = position
        }
    }

    fun onLoopClick(trackId: Long?, player: Player, save: (Long, Long, Long) -> Unit) {
        val button = ABLoopButtonState(loopStartMs, loopEndMs, isLooping)
        when (val action = ABLoopButtonStateMachine.onClick(button, positionMs, durationMs)) {
            is ABLoopButtonAction.SetStart -> {
                loopTrackId = trackId
                loopStartMs = action.startMs
                loopEndMs = null
                isLooping = false
            }
            is ABLoopButtonAction.SetEndAndStartLoop -> {
                val id = trackId ?: return
                loopTrackId = id
                loopStartMs = action.range.startMs
                loopEndMs = action.range.endMs
                save(id, action.range.startMs, action.range.endMs)
                player.seekTo(action.range.startMs)
                positionMs = action.range.startMs
                isLooping = true
            }
            is ABLoopButtonAction.StartLoop -> {
                player.seekTo(action.range.startMs)
                positionMs = action.range.startMs
                isLooping = true
            }
            ABLoopButtonAction.StopLoop -> isLooping = false
            ABLoopButtonAction.Clear, ABLoopButtonAction.None -> Unit
        }
    }

    fun clearLoop(trackId: Long?, delete: (Long) -> Unit) {
        trackId?.let(delete)
        loopStartMs = null
        loopEndMs = null
        isLooping = false
    }

    fun changeTrack(trackId: Long?) {
        loopTrackId = trackId
        loopStartMs = null
        loopEndMs = null
        isLooping = false
    }

    fun restoreLoop(trackId: Long?, saved: TrackLoop?) {
        if (saved != null && saved.trackId == trackId) {
            loopTrackId = saved.trackId
            loopStartMs = saved.startMs
            loopEndMs = saved.endMs
        } else if (saved == null && loopTrackId == trackId) {
            loopStartMs = null
            loopEndMs = null
        }
        isLooping = false
    }
}

@Composable
internal fun rememberPlayerScreenState(player: Player): PlayerScreenState {
    val state = remember(player) { PlayerScreenState(player) }
    DisposableEffect(player, state) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = state.update(player)
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player, state) {
        while (true) {
            state.tick(player)
            delay(500)
        }
    }
    return state
}

@Composable
internal fun BindTrackLoop(state: PlayerScreenState, trackId: Long?, saved: TrackLoop?) {
    LaunchedEffect(trackId) { state.changeTrack(trackId) }
    LaunchedEffect(trackId, saved) { state.restoreLoop(trackId, saved) }
}
