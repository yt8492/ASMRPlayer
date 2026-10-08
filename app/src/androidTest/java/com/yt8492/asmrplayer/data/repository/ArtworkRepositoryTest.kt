package com.yt8492.asmrplayer.data.repository

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yt8492.asmrplayer.data.local.database.AppDatabase
import com.yt8492.asmrplayer.data.datasource.document.ArtworkPermissionSource
import com.yt8492.asmrplayer.data.model.QueueArtworkTarget
import com.yt8492.asmrplayer.data.repository.impl.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArtworkRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var tracks: TrackArtworkRepository
    private lateinit var queues: QueueArtworkRepository
    private lateinit var repository: ArtworkRepository
    private val acquired = mutableListOf<Uri>()
    private val released = mutableListOf<Uri>()
    private var denyPermission = false
    private val image = Uri.parse("content://test/image")
    private val target = QueueArtworkTarget.Playlist(7)
    private val permissions = object : ArtworkPermissionSource {
        override fun acquire(uri: Uri) { if (denyPermission) throw SecurityException(); acquired.add(uri) }
        override fun release(uri: Uri) { released.add(uri) }
    }
    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        tracks = TrackArtworkRepositoryImpl(database.trackArtworkDao())
        queues = QueueArtworkRepositoryImpl(database.queueArtworkDao())
        repository = ArtworkRepositoryImpl(tracks, queues, permissions)
    }
    @After fun tearDown() { database.close() }

    @Test fun sharedUriIsReleasedOnlyAfterLastTrackOrQueueReferenceIsRemoved() = runTest {
        repository.saveTrackArtwork(10, image)
        repository.saveQueueArtwork(target, image)
        repository.deleteTrackArtwork(10)
        assertTrue(released.isEmpty())
        repository.deleteQueueArtwork(target)
        assertEquals(listOf(image), released)
        assertEquals(listOf(image, image), acquired)
    }
    @Test fun replacementKeepsUrisStillUsedByAnotherTrack() = runTest {
        repository.saveTrackArtwork(10, image)
        repository.saveTrackArtwork(11, image)
        val replacement = Uri.parse("content://test/new")
        repository.saveTrackArtwork(10, replacement)
        assertTrue(released.isEmpty())
        repository.deleteTrackArtwork(11)
        assertEquals(listOf(image), released)
        assertEquals(replacement, tracks.getTrackArtwork(10)?.imageUri)
    }
    @Test fun deniedUriPermissionDoesNotOverwriteSavedArtwork() = runTest {
        repository.saveTrackArtwork(10, image)
        denyPermission = true
        try {
            repository.saveTrackArtwork(10, Uri.parse("content://test/new"))
            fail()
        } catch (_: SecurityException) { }
        assertEquals(image, tracks.getTrackArtwork(10)?.imageUri)
        assertTrue(released.isEmpty())
    }
    @Test fun failedDatabaseWriteReleasesNewGrantAndPreservesPreviousArtwork() = runTest {
        repository.saveTrackArtwork(10, image)
        val failingTracks = object : TrackArtworkRepository by tracks {
            override suspend fun saveTrackArtwork(trackId: Long, imageUri: Uri) { throw java.io.IOException("保存失敗") }
        }
        val failing = ArtworkRepositoryImpl(failingTracks, queues, permissions)
        val next = Uri.parse("content://test/new")
        try { failing.saveTrackArtwork(10, next); fail() } catch (_: java.io.IOException) { }
        assertEquals(listOf(next), released)
        assertEquals(image, tracks.getTrackArtwork(10)?.imageUri)
    }

    @Test fun trackArtworkHasPriorityOverQueueAndMetadata() = runTest {
        val fallback = Uri.parse("content://test/metadata")
        repository.saveQueueArtwork(target, image)
        val track = Uri.parse("content://test/track")
        repository.saveTrackArtwork(10, track)
        assertEquals(track, repository.observeResolvedArtwork(10, target, fallback).first())
        repository.deleteTrackArtwork(10)
        assertEquals(image, repository.observeResolvedArtwork(10, target, fallback).first())
        repository.deleteQueueArtwork(target)
        assertEquals(fallback, repository.observeResolvedArtwork(10, target, fallback).first())
    }
}
