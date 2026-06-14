package com.yt8492.asmrplayer.ui.player

data class TrackLoopRange(
    val startMs: Long,
    val endMs: Long,
) {
    fun contains(positionMs: Long): Boolean {
        return positionMs in startMs..endMs
    }
}

object TrackLoopRangeFactory {
    fun create(startMs: Long?, endMs: Long?, durationMs: Long): TrackLoopRange? {
        val start = startMs ?: return null
        val end = endMs ?: return null
        val safeDuration = durationMs.coerceAtLeast(0L)
        val clampedStart = start.coerceIn(0L, safeDuration)
        val clampedEnd = end.coerceIn(0L, safeDuration)
        if (clampedStart >= clampedEnd) return null
        return TrackLoopRange(clampedStart, clampedEnd)
    }

    fun shouldStopLoopAfterUserSeek(range: TrackLoopRange, positionMs: Long): Boolean {
        return !range.contains(positionMs)
    }
}

data class ABLoopButtonState(
    val startMs: Long?,
    val endMs: Long?,
    val isLooping: Boolean,
)

sealed interface ABLoopButtonAction {
    data class SetStart(val startMs: Long) : ABLoopButtonAction
    data class SetEndAndStartLoop(val range: TrackLoopRange) : ABLoopButtonAction
    data class StartLoop(val range: TrackLoopRange) : ABLoopButtonAction
    data object StopLoop : ABLoopButtonAction
    data object Clear : ABLoopButtonAction
    data object None : ABLoopButtonAction
}

object ABLoopButtonStateMachine {
    fun onClick(state: ABLoopButtonState, positionMs: Long, durationMs: Long): ABLoopButtonAction {
        if (state.isLooping) return ABLoopButtonAction.StopLoop

        val savedRange = TrackLoopRangeFactory.create(state.startMs, state.endMs, durationMs)
        if (savedRange != null) return ABLoopButtonAction.StartLoop(savedRange)

        val safeDuration = durationMs.coerceAtLeast(0L)
        val clampedPositionMs = positionMs.coerceIn(0L, safeDuration)
        val startMs = state.startMs ?: return ABLoopButtonAction.SetStart(clampedPositionMs)
        val newRange = TrackLoopRangeFactory.create(startMs, clampedPositionMs, durationMs)
            ?: return ABLoopButtonAction.None
        return ABLoopButtonAction.SetEndAndStartLoop(newRange)
    }

    fun onLongClick(): ABLoopButtonAction {
        return ABLoopButtonAction.Clear
    }
}
