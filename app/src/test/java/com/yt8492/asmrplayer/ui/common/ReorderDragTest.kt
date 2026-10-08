package com.yt8492.asmrplayer.ui.common

import org.junit.Assert.*
import org.junit.Test

class ReorderDragTest {
    @Test fun largeDragMovesEachCrossedRowInOrder() {
        val moves = mutableListOf<Pair<Int, Int>>()
        val result = moveDraggedItem(0, 170f, 72f, 3) { from, to -> moves.add(from to to) }
        assertEquals(listOf(0 to 1, 1 to 2), moves)
        assertEquals(2, result.index)
        assertEquals(26f, result.offset, 0f)
    }
    @Test fun upwardDragNeverMovesBeforeFirstRow() {
        val moves = mutableListOf<Pair<Int, Int>>()
        val result = moveDraggedItem(1, -200f, 72f, 3) { from, to -> moves.add(from to to) }
        assertEquals(listOf(1 to 0), moves)
        assertEquals(0, result.index)
    }
    @Test fun thresholdDoesNotMoveUntilExceeded() {
        val result = moveDraggedItem(0, 72f, 72f, 3) { _, _ -> fail() }
        assertEquals(0, result.index)
    }
}
