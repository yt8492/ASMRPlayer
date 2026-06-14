package com.yt8492.asmrplayer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackLoopRangeTest {
    @Test
    fun create_開始が終了より前なら有効な区間を返す() {
        val result = TrackLoopRangeFactory.create(
            startMs = 1_000L,
            endMs = 3_000L,
            durationMs = 5_000L,
        )

        assertEquals(TrackLoopRange(startMs = 1_000L, endMs = 3_000L), result)
    }

    @Test
    fun create_終了が開始以下ならnullを返す() {
        assertNull(TrackLoopRangeFactory.create(startMs = 3_000L, endMs = 3_000L, durationMs = 5_000L))
        assertNull(TrackLoopRangeFactory.create(startMs = 4_000L, endMs = 3_000L, durationMs = 5_000L))
    }

    @Test
    fun create_durationを超える値は範囲内に丸める() {
        val result = TrackLoopRangeFactory.create(
            startMs = -1_000L,
            endMs = 8_000L,
            durationMs = 5_000L,
        )

        assertEquals(TrackLoopRange(startMs = 0L, endMs = 5_000L), result)
    }

    @Test
    fun shouldStopLoopAfterUserSeek_区間外なら停止する() {
        val range = TrackLoopRange(startMs = 1_000L, endMs = 3_000L)

        assertTrue(TrackLoopRangeFactory.shouldStopLoopAfterUserSeek(range, 999L))
        assertTrue(TrackLoopRangeFactory.shouldStopLoopAfterUserSeek(range, 3_001L))
    }

    @Test
    fun shouldStopLoopAfterUserSeek_区間内なら継続する() {
        val range = TrackLoopRange(startMs = 1_000L, endMs = 3_000L)

        assertFalse(TrackLoopRangeFactory.shouldStopLoopAfterUserSeek(range, 1_000L))
        assertFalse(TrackLoopRangeFactory.shouldStopLoopAfterUserSeek(range, 2_000L))
        assertFalse(TrackLoopRangeFactory.shouldStopLoopAfterUserSeek(range, 3_000L))
    }

    @Test
    fun abLoopButton_未設定ならA点を設定する() {
        val action = ABLoopButtonStateMachine.onClick(
            state = ABLoopButtonState(startMs = null, endMs = null, isLooping = false),
            positionMs = 2_000L,
            durationMs = 5_000L,
        )

        assertEquals(ABLoopButtonAction.SetStart(startMs = 2_000L), action)
    }

    @Test
    fun abLoopButton_A点設定済みで有効なB点なら区間を作る() {
        val action = ABLoopButtonStateMachine.onClick(
            state = ABLoopButtonState(startMs = 1_000L, endMs = null, isLooping = false),
            positionMs = 3_000L,
            durationMs = 5_000L,
        )

        assertEquals(
            ABLoopButtonAction.SetEndAndStartLoop(TrackLoopRange(startMs = 1_000L, endMs = 3_000L)),
            action,
        )
    }

    @Test
    fun abLoopButton_A点設定済みでB点候補がA点以下なら何もしない() {
        val action = ABLoopButtonStateMachine.onClick(
            state = ABLoopButtonState(startMs = 3_000L, endMs = null, isLooping = false),
            positionMs = 2_000L,
            durationMs = 5_000L,
        )

        assertEquals(ABLoopButtonAction.None, action)
    }

    @Test
    fun abLoopButton_保存済み停止中ならループを開始する() {
        val action = ABLoopButtonStateMachine.onClick(
            state = ABLoopButtonState(startMs = 1_000L, endMs = 3_000L, isLooping = false),
            positionMs = 2_000L,
            durationMs = 5_000L,
        )

        assertEquals(
            ABLoopButtonAction.StartLoop(TrackLoopRange(startMs = 1_000L, endMs = 3_000L)),
            action,
        )
    }

    @Test
    fun abLoopButton_ループ中なら停止する() {
        val action = ABLoopButtonStateMachine.onClick(
            state = ABLoopButtonState(startMs = 1_000L, endMs = 3_000L, isLooping = true),
            positionMs = 2_000L,
            durationMs = 5_000L,
        )

        assertEquals(ABLoopButtonAction.StopLoop, action)
    }

    @Test
    fun abLoopButton_長押しなら解除する() {
        assertEquals(ABLoopButtonAction.Clear, ABLoopButtonStateMachine.onLongClick())
    }
}
