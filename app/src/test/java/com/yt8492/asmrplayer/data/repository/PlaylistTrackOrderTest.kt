package com.yt8492.asmrplayer.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistTrackOrderTest {
    @Test
    fun move_後ろへ移動すると順序が入れ替わる() {
        val result = PlaylistTrackOrder.move(
            trackIds = listOf(1L, 2L, 3L, 4L),
            fromIndex = 1,
            toIndex = 3,
        )

        assertEquals(listOf(1L, 3L, 4L, 2L), result)
    }

    @Test
    fun move_前へ移動すると順序が入れ替わる() {
        val result = PlaylistTrackOrder.move(
            trackIds = listOf(1L, 2L, 3L, 4L),
            fromIndex = 3,
            toIndex = 1,
        )

        assertEquals(listOf(1L, 4L, 2L, 3L), result)
    }

    @Test
    fun move_範囲外なら元の順序を返す() {
        val trackIds = listOf(1L, 2L, 3L)

        assertEquals(trackIds, PlaylistTrackOrder.move(trackIds, fromIndex = -1, toIndex = 1))
        assertEquals(trackIds, PlaylistTrackOrder.move(trackIds, fromIndex = 0, toIndex = 3))
    }

    @Test
    fun remove_指定したトラックを取り除く() {
        val result = PlaylistTrackOrder.remove(
            trackIds = listOf(1L, 2L, 3L),
            trackId = 2L,
        )

        assertEquals(listOf(1L, 3L), result)
    }
}
