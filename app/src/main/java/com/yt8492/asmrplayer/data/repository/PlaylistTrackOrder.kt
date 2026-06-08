package com.yt8492.asmrplayer.data.repository

object PlaylistTrackOrder {
    fun remove(trackIds: List<Long>, trackId: Long): List<Long> {
        return trackIds.filterNot { it == trackId }
    }

    fun move(trackIds: List<Long>, fromIndex: Int, toIndex: Int): List<Long> {
        if (fromIndex !in trackIds.indices || toIndex !in trackIds.indices || fromIndex == toIndex) {
            return trackIds
        }
        val mutableTrackIds = trackIds.toMutableList()
        val movedTrackId = mutableTrackIds.removeAt(fromIndex)
        mutableTrackIds.add(toIndex, movedTrackId)
        return mutableTrackIds
    }
}
