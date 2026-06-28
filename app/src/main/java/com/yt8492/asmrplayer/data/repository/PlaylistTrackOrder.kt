package com.yt8492.asmrplayer.data.repository

object PlaylistTrackOrder {
    fun move(itemIds: List<Long>, fromIndex: Int, toIndex: Int): List<Long> {
        if (fromIndex !in itemIds.indices || toIndex !in itemIds.indices || fromIndex == toIndex) {
            return itemIds
        }
        val mutableItemIds = itemIds.toMutableList()
        val movedItemId = mutableItemIds.removeAt(fromIndex)
        mutableItemIds.add(toIndex, movedItemId)
        return mutableItemIds
    }
}
