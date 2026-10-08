package com.yt8492.asmrplayer.domain

import com.yt8492.asmrplayer.data.model.PlaylistTrack
import com.yt8492.asmrplayer.data.model.PlaylistTrackItem
import com.yt8492.asmrplayer.data.repository.TrackRepository

/** 行順・重複行を保ち、現在アクセスできない曲だけを除外する。 */
class ResolvePlaylistTracks(private val tracks: TrackRepository) {
    suspend operator fun invoke(rows: List<PlaylistTrack>): List<PlaylistTrackItem> {
        val tracksById = tracks.getTracks(rows.map { it.trackId }).associateBy { it.id }
        return rows.mapNotNull { row -> tracksById[row.trackId]?.let { PlaylistTrackItem(row.id, it) } }
    }
}
