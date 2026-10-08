package com.yt8492.asmrplayer.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yt8492.asmrplayer.data.local.database.AppDatabase
import com.yt8492.asmrplayer.data.repository.impl.PlaylistRepositoryImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: PlaylistRepository
    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repository = PlaylistRepositoryImpl(database.playlistDao(), now = { 123 })
    }
    @After fun tearDown() { database.close() }

    @Test fun duplicateTracksKeepDistinctRowsThroughReorderAndRemoval() = runTest {
        val id = repository.createPlaylist("作品")
        repository.addTracks(id, listOf(10, 20, 10))
        val before = repository.getPlaylistTracks(id)
        assertEquals(3, before.map { it.id }.toSet().size)
        val order = listOf(before[2].id, before[0].id, before[1].id)
        repository.replaceTrackOrder(id, order)
        assertEquals(order, repository.getPlaylistTracks(id).map { it.id })
        repository.removeTrack(id, before[0].id)
        assertEquals(listOf(before[2].id, before[1].id), repository.getPlaylistTracks(id).map { it.id })
        assertEquals(listOf(0, 1), database.playlistDao().getPlaylistTracks(id).map { it.position })
    }
    @Test fun simultaneousSingleTrackAppendsUseContiguousPositions() = runTest {
        val id = repository.createPlaylist("作品")
        coroutineScope {
            (0 until 12).map { index -> async(Dispatchers.Default) { repository.addTrack(id, index.toLong()) } }.awaitAll()
        }
        val rows = database.playlistDao().getPlaylistTracks(id)
        assertEquals((0 until 12).toList(), rows.map { it.position })
        assertEquals(12, rows.map { it.trackId }.toSet().size)
        assertEquals(12, repository.getPlaylist(id)!!.trackCount)
    }
}
