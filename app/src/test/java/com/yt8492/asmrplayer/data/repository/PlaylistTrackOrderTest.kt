package com.yt8492.asmrplayer.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistTrackOrderTest {
    @Test
    fun move_後ろへ移動すると順序が入れ替わる() {
        val result = PlaylistTrackOrder.move(
            itemIds = listOf(1L, 2L, 3L, 4L),
            fromIndex = 1,
            toIndex = 3,
        )

        assertEquals(listOf(1L, 3L, 4L, 2L), result)
    }

    @Test
    fun move_前へ移動すると順序が入れ替わる() {
        val result = PlaylistTrackOrder.move(
            itemIds = listOf(1L, 2L, 3L, 4L),
            fromIndex = 3,
            toIndex = 1,
        )

        assertEquals(listOf(1L, 4L, 2L, 3L), result)
    }

    @Test
    fun move_範囲外なら元の順序を返す() {
        val itemIds = listOf(1L, 2L, 3L)

        assertEquals(itemIds, PlaylistTrackOrder.move(itemIds, fromIndex = -1, toIndex = 1))
        assertEquals(itemIds, PlaylistTrackOrder.move(itemIds, fromIndex = 0, toIndex = 3))
    }

    @Test
    fun move_同じトラックID相当の項目でも項目IDで順序を維持する() {
        val result = PlaylistTrackOrder.move(
            itemIds = listOf(10L, 11L, 12L),
            fromIndex = 0,
            toIndex = 2,
        )

        assertEquals(listOf(11L, 12L, 10L), result)
    }
}
