package com.yt8492.asmrplayer.playback

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yt8492.asmrplayer.data.model.Track
import com.yt8492.asmrplayer.playback.model.*
import com.yt8492.asmrplayer.navigation.toPlayerRoute
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackRequestCodecTest {
    @Test fun playlistNotificationKeepsDuplicateTrackIndexAndTitle() {
        val request = PlaybackRequest(PlaybackQueue.Playlist(7, "A & B / 日本語"), 42, 3)
        val restored = Intent().putPlaybackRequest(request).toPlaybackRequest()!!
        assertEquals(request.queue, restored.queue)
        assertEquals(42L, restored.trackId)
        assertEquals(3, restored.startIndex)
        assertEquals(request.toPlayerRoute(), restored.toPlayerRoute())
    }
    @Test fun folderMetadataAndNotificationUseSameQueue() {
        val queue = PlaybackQueue.Folder("saf:日本語?x=1&y=2/", "PDF & 音声")
        val track = Track(42, "曲", "作者", durationMs = 1000, fileSizeBytes = 100, trackNumber = 1, uri = Uri.parse("content://test/42"))
        val item = track.toMediaItem(queue)
        val request = item.toPlaybackRequest(2)!!
        assertEquals(queue, request.queue)
        assertEquals(2, request.startIndex)
        assertEquals(queue, Intent().putPlaybackRequest(request).toPlaybackRequest()!!.queue)
        assertTrue(request.toPlayerRoute().contains(Uri.encode("saf:日本語?x=1&y=2/")))
    }
    @Test fun invalidNotificationIsRejected() {
        assertNull(Intent().toPlaybackRequest())
        val invalid = Intent().putPlaybackRequest(PlaybackRequest(PlaybackQueue.Playlist(-1, ""), 1))
        assertNull(invalid.toPlaybackRequest())
        assertNull(Intent().putPlaybackRequest(PlaybackRequest(PlaybackQueue.Folder("", ""), -1)).toPlaybackRequest())
    }
    @Test fun selectedPlaylistRowIsEncodedAlongsidePosition() {
        val request = PlaybackRequest(PlaybackQueue.Playlist(7, "A?B#C"), 42, 3, playlistTrackId = 99)
        assertTrue(request.toPlayerRoute().contains("playlistTrackId=99&startIndex=3"))
        assertTrue(request.toPlayerRoute().contains("name=A%3FB%23C"))
    }
}
