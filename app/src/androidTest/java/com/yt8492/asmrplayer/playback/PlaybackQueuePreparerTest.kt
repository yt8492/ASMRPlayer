package com.yt8492.asmrplayer.playback

import android.net.Uri
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yt8492.asmrplayer.data.model.Track
import com.yt8492.asmrplayer.playback.model.PlaybackQueue
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlaybackQueuePreparerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var player: ExoPlayer
    private val tracks = listOf(Track(10, "曲", "", durationMs = 1000, fileSizeBytes = null, trackNumber = 0, uri = Uri.parse("content://test/10")))
    @Before fun setUp() = instrumentation.runOnMainSync { player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build() }
    @After fun tearDown() = instrumentation.runOnMainSync { player.release() }

    @Test fun reopeningSameQueuePreservesPositionAndPausedState() = instrumentation.runOnMainSync {
        val queue = PlaybackQueue.Playlist(7, "作品")
        player.setMediaItems(tracks.map { it.toMediaItem(queue) })
        player.seekTo(0, 500)
        player.pause()
        player.preparePlaybackQueue(queue, tracks, 10, 0)
        assertEquals(500L, player.currentPosition)
        assertFalse(player.playWhenReady)
    }
    @Test fun sameTracksInDifferentQueueUpdateNotificationMetadata() = instrumentation.runOnMainSync {
        val old = PlaybackQueue.Playlist(7, "作品")
        val new = PlaybackQueue.Folder("saf:folder/", "フォルダ")
        player.setMediaItems(tracks.map { it.toMediaItem(old) })
        // prepareとplayはI/Oを始めずに記録する。キュー操作は実際のExoPlayerに委譲する。
        val wrapper = object : ForwardingPlayer(player) {
            override fun prepare() = Unit
            override fun setPlayWhenReady(playWhenReady: Boolean) = Unit
        }
        wrapper.preparePlaybackQueue(new, tracks, 10, 0)
        assertEquals(new, player.currentMediaItem!!.mediaMetadata.extras!!.toPlaybackQueue())
    }
    @Test fun duplicateTrackSelectionUsesRequestedIndex() = instrumentation.runOnMainSync {
        val queue = PlaybackQueue.Playlist(7, "作品")
        player.setMediaItems(listOf(tracks.single().toMediaItem(queue), tracks.single().toMediaItem(queue)))
        val wrapper = object : ForwardingPlayer(player) { override fun play() = Unit }
        wrapper.preparePlaybackQueue(queue, listOf(tracks.single(), tracks.single()), 10, 1)
        assertEquals(1, player.currentMediaItemIndex)
    }
}
