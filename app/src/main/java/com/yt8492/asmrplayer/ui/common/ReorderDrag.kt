package com.yt8492.asmrplayer.ui.common

internal data class DragMovement(val index: Int, val offset: Float)

/** 1回の大きなドラッグでも各行を順番に移動する。上下端では範囲外へ移動しない。 */
internal fun moveDraggedItem(index: Int, offset: Float, rowHeight: Float, lastIndex: Int,
    onMove: (Int, Int) -> Unit): DragMovement {
    require(rowHeight > 0)
    var current = index
    var remaining = offset
    while (remaining > rowHeight && current < lastIndex) {
        onMove(current, current + 1)
        current += 1
        remaining -= rowHeight
    }
    while (remaining < -rowHeight && current > 0) {
        onMove(current, current - 1)
        current -= 1
        remaining += rowHeight
    }
    return DragMovement(current, remaining)
}
