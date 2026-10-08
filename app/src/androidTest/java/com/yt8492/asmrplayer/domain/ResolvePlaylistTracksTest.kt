package com.yt8492.asmrplayer.domain

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yt8492.asmrplayer.data.model.*
import com.yt8492.asmrplayer.data.repository.TrackRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResolvePlaylistTracksTest {
    @Test fun missingTracksAreFilteredWhileDuplicateRowsKeepIdentityAndOrder() = runTest {
        val track = Track(10, "曲", "", durationMs = 1000, fileSizeBytes = null, trackNumber = 0, uri = Uri.EMPTY)
        val tracks = object : TrackRepository {
            override suspend fun getTracks(trackIds: List<Long>) = listOf(track)
            override suspend fun getTracksInDirectory(directoryPath: String) = error("unused")
        }
        val resolved = ResolvePlaylistTracks(tracks)(listOf(PlaylistTrack(101, 10), PlaylistTrack(102, 20), PlaylistTrack(103, 10)))
        assertEquals(listOf(101L, 103L), resolved.map { it.playlistTrackId })
        assertEquals(listOf(10L, 10L), resolved.map { it.track.id })
    }
}
