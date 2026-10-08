package com.yt8492.asmrplayer.ui.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yt8492.asmrplayer.data.model.TrackLoop
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlayerScreenStateTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var actual: ExoPlayer
    private lateinit var player: TestPlayer
    private lateinit var state: PlayerScreenState
    @Before fun setUp() = instrumentation.runOnMainSync {
        actual = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        player = TestPlayer(actual)
        state = PlayerScreenState(player)
    }
    @After fun tearDown() = instrumentation.runOnMainSync { actual.release() }

    @Test fun reachingLoopEndSeeksBackToStart() = instrumentation.runOnMainSync {
        state.loopStartMs = 100
        state.loopEndMs = 800
        state.isLooping = true
        player.position = 800
        state.tick(player)
        assertEquals(100L, player.lastSeek)
        assertEquals(100L, state.positionMs)
    }
    @Test fun savedRangeIsRestoredWithoutStartingLoopAutomatically() = instrumentation.runOnMainSync {
        state.isLooping = true
        state.restoreLoop(10, TrackLoop(10, 100, 800))
        assertEquals(100L, state.loopStartMs)
        assertEquals(800L, state.loopEndMs)
        assertFalse(state.isLooping)
    }
    @Test fun selectingTwoPositionsSavesRangeAndLongPressClearsIt() = instrumentation.runOnMainSync {
        val saved = mutableListOf<Triple<Long, Long, Long>>()
        val save: (Long, Long, Long) -> Unit = { id, start, end -> saved.add(Triple(id, start, end)) }
        state.durationMs = 1000
        state.positionMs = 100
        state.onLoopClick(10, player, save)
        assertFalse(state.isLooping)
        state.positionMs = 800
        state.onLoopClick(10, player, save)
        assertEquals(listOf(Triple(10L, 100L, 800L)), saved)
        assertTrue(state.isLooping)
        assertEquals(100L, player.lastSeek)
        var deleted: Long? = null
        state.clearLoop(10) { deleted = it }
        assertEquals(10L, deleted)
        assertNull(state.loopStartMs)
        assertFalse(state.isLooping)
    }

    @Test fun changingTrackClearsRangeAndStopsLoop() = instrumentation.runOnMainSync {
        state.restoreLoop(10, TrackLoop(10, 100, 800))
        state.isLooping = true
        state.changeTrack(11)
        assertEquals(11L, state.loopTrackId)
        assertNull(state.loopStartMs)
        assertNull(state.loopEndMs)
        assertFalse(state.isLooping)
    }
    @Test fun unknownDurationDoesNotSeekUsingSavedRange() = instrumentation.runOnMainSync {
        state.loopStartMs = 100
        state.loopEndMs = 800
        state.isLooping = true
        player.length = androidx.media3.common.C.TIME_UNSET
        player.position = 800
        state.tick(player)
        assertNull(player.lastSeek)
        assertEquals(0L, state.durationMs)
    }

    private class TestPlayer(player: androidx.media3.common.Player) : ForwardingPlayer(player) {
        var position = 0L
        var length = 1000L
        var lastSeek: Long? = null
        override fun getCurrentPosition() = position
        override fun getDuration() = length
        override fun seekTo(positionMs: Long) { lastSeek = positionMs }
    }
}
