package com.yt8492.asmrplayer.playback.model

import com.yt8492.asmrplayer.data.model.PlaylistTrack
import com.yt8492.asmrplayer.data.model.Track

internal fun resolvePlaybackStartIndex(
    tracks: List<Track>,
    startTrackId: Long,
    startIndexHint: Int?,
): Int {
    return resolvePlaybackStartIndexByTrackIds(
        trackIds = tracks.map { it.id },
        startTrackId = startTrackId,
        startIndexHint = startIndexHint,
    )
}

internal fun resolvePlaybackStartIndexByTrackIds(
    trackIds: List<Long>,
    startTrackId: Long,
    startIndexHint: Int?,
): Int {
    return startIndexHint
        ?.takeIf { it in trackIds.indices && trackIds[it] == startTrackId }
        ?: trackIds.indexOfFirst { it == startTrackId }.takeIf { it >= 0 }
        ?: 0
}

internal fun resolvePlaylistPlaybackStartIndex(
    tracks: List<Track>,
    playlistTracks: List<PlaylistTrack>,
    startTrackId: Long,
    startPlaylistTrackId: Long?,
    startIndexHint: Int?,
): Int {
    return resolvePlaylistPlaybackStartIndexByTrackIds(
        trackIds = tracks.map { it.id },
        playlistTracks = playlistTracks,
        startTrackId = startTrackId,
        startPlaylistTrackId = startPlaylistTrackId,
        startIndexHint = startIndexHint,
    )
}

internal fun resolvePlaylistPlaybackStartIndexByTrackIds(
    trackIds: List<Long>,
    playlistTracks: List<PlaylistTrack>,
    startTrackId: Long,
    startPlaylistTrackId: Long?,
    startIndexHint: Int?,
): Int {
    val availableTrackIds = trackIds.toSet()
    val availablePlaylistTracks = playlistTracks.filter { it.trackId in availableTrackIds }
    val playlistTrackIndex = startPlaylistTrackId
        ?.let { targetId -> availablePlaylistTracks.indexOfFirst { it.id == targetId } }
        ?.takeIf { it >= 0 }

    return playlistTrackIndex ?: resolvePlaybackStartIndexByTrackIds(
        trackIds = trackIds,
        startTrackId = startTrackId,
        startIndexHint = startIndexHint,
    )
}
