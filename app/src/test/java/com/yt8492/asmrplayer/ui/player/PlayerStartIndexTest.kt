package com.yt8492.asmrplayer.ui.player

import com.yt8492.asmrplayer.data.model.PlaylistTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerStartIndexTest {
    @Test
    fun resolvePlaylistPlaybackStartIndex_同じトラックIDでもプレイリスト行IDの位置を返す() {
        val result = resolvePlaylistPlaybackStartIndexByTrackIds(
            trackIds = listOf(1L, 1L, 2L),
            playlistTracks = listOf(
                PlaylistTrack(id = 10L, trackId = 1L),
                PlaylistTrack(id = 11L, trackId = 1L),
                PlaylistTrack(id = 12L, trackId = 2L),
            ),
            startTrackId = 1L,
            startPlaylistTrackId = 11L,
            startIndexHint = null,
        )

        assertEquals(1, result)
    }

    @Test
    fun resolvePlaylistPlaybackStartIndex_行IDがない場合は一致する開始Indexを使う() {
        val result = resolvePlaylistPlaybackStartIndexByTrackIds(
            trackIds = listOf(1L, 1L, 2L),
            playlistTracks = listOf(
                PlaylistTrack(id = 10L, trackId = 1L),
                PlaylistTrack(id = 11L, trackId = 1L),
                PlaylistTrack(id = 12L, trackId = 2L),
            ),
            startTrackId = 1L,
            startPlaylistTrackId = null,
            startIndexHint = 1,
        )

        assertEquals(1, result)
    }

    @Test
    fun resolvePlaybackStartIndex_開始Indexのトラックが違う場合は先頭一致へ戻す() {
        val result = resolvePlaybackStartIndexByTrackIds(
            trackIds = listOf(1L, 2L, 1L),
            startTrackId = 1L,
            startIndexHint = 1,
        )

        assertEquals(0, result)
    }
}
