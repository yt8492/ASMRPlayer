package com.yt8492.asmrplayer.ui

import android.net.Uri
import com.yt8492.asmrplayer.data.model.*
import com.yt8492.asmrplayer.data.repository.*
import com.yt8492.asmrplayer.ui.fileexplorer.FileExplorerViewModel
import com.yt8492.asmrplayer.ui.playlist.PlaylistListViewModel
import com.yt8492.asmrplayer.ui.settings.SettingsViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class RefactorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = mutableListOf<androidx.lifecycle.ViewModel>()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    @Test fun newerFolderLoadCancelsOlderLoadWithoutPublishingError() = runTest(dispatcher) {
        var cancelled = false
        val files = object : FileExplorerRepository {
            override suspend fun getContent(directoryPath: String): FileExplorerContent {
                if (directoryPath == "old/") {
                    try { awaitCancellation() } finally { cancelled = true }
                }
                return FileExplorerContent(currentPath = directoryPath, directories = emptyList(), tracks = emptyList(), images = emptyList())
            }
            override suspend fun scanDirectory(directoryPath: String) = true
        }
        val model = FileExplorerViewModel(files, FakePlaylists(), FakeTracks()).also(models::add)
        model.loadContent("old")
        runCurrent()
        model.loadContent("new")
        advanceUntilIdle()
        assertTrue(cancelled)
        assertEquals("new/", model.uiState.value.currentPath)
        assertFalse(model.uiState.value.isLoading)
        assertNull(model.uiState.value.errorMessage)
    }

    @Test fun failedRefreshKeepsCurrentContentAndClearsRefreshingFlag() = runTest(dispatcher) {
        val files = object : FileExplorerRepository {
            override suspend fun getContent(directoryPath: String) = FileExplorerContent(currentPath = directoryPath, directories = emptyList(), tracks = emptyList(), images = emptyList(), directoryTitle = "現在の一覧")
            override suspend fun scanDirectory(directoryPath: String): Boolean = throw java.io.IOException("切断")
        }
        val model = FileExplorerViewModel(files, FakePlaylists(), FakeTracks()).also(models::add)
        model.loadContent("folder")
        advanceUntilIdle()
        model.refreshContent()
        advanceUntilIdle()
        assertEquals("現在の一覧", model.uiState.value.directoryTitle)
        assertFalse(model.uiState.value.isRefreshing)
        assertNotNull(model.uiState.value.errorMessage)
    }

    @Test fun cancellingPlaylistCreationDoesNotPublishFailureMessage() = runTest(dispatcher) {
        val playlists = object : PlaylistRepository by FakePlaylists() {
            override suspend fun createPlaylist(name: String): Long = throw CancellationException()
        }
        val model = PlaylistListViewModel(playlists).also(models::add)
        model.createPlaylist("test")
        advanceUntilIdle()
        assertNull(model.uiState.value.errorMessage)
    }

    @Test fun settingsReadFailureCannotLeaveLoadingSpinnerRunning() = runTest(dispatcher) {
        var reads = 0
        val folders = object : LibraryFolderRepository {
            override fun observeFolders() = flowOf(emptyList<LibraryFolder>())
            override suspend fun getFolders(): List<LibraryFolder> {
                reads += 1
                throw java.io.IOException("読込失敗")
            }
            override fun hasPermission(uri: String) = true
            override suspend fun addFolder(uri: Uri) = Unit
            override suspend fun reloadFolder(uri: String) = Unit
            override suspend fun restoreFolderAccess(folderUri: String, selectedUri: Uri) = Unit
            override suspend fun removeFolder(uri: String) = Unit
            override suspend fun rootDirectories() = emptyList<BrowsableDirectory>()
            override suspend fun getContent(directoryPath: String): FileExplorerContent = error("unused")
            override suspend fun getTracksInDirectory(directoryPath: String) = emptyList<Track>()
            override suspend fun getTracks(ids: List<Long>) = emptyList<Track>()
        }
        val model = SettingsViewModel(folders).also(models::add)
        model.reloadAll()
        advanceUntilIdle()
        assertFalse(model.uiState.value.isLoading)
        assertEquals("", model.uiState.value.loadingLabel)
        assertNotNull(model.uiState.value.message)
        assertEquals(2, reads)
    }
}

private class FakeTracks : TrackRepository {
    override suspend fun getTracks(trackIds: List<Long>) = emptyList<Track>()
    override suspend fun getTracksInDirectory(directoryPath: String) = emptyList<Track>()
}
private class FakePlaylists : PlaylistRepository {
    override fun observePlaylists() = flowOf(emptyList<Playlist>())
    override fun observePlaylistTracks(playlistId: Long) = flowOf(emptyList<PlaylistTrack>())
    override suspend fun getPlaylist(playlistId: Long): Playlist? = null
    override suspend fun getPlaylistTracks(playlistId: Long) = emptyList<PlaylistTrack>()
    override suspend fun getTrackIds(playlistId: Long) = emptyList<Long>()
    override suspend fun createPlaylist(name: String) = 1L
    override suspend fun renamePlaylist(playlistId: Long, name: String) = Unit
    override suspend fun deletePlaylist(playlistId: Long) = Unit
    override suspend fun addTrack(playlistId: Long, trackId: Long) = AddTrackResult.Added
    override suspend fun addTracks(playlistId: Long, trackIds: List<Long>) = AddTracksResult(trackIds.size, 0)
    override suspend fun removeTrack(playlistId: Long, playlistTrackId: Long) = Unit
    override suspend fun replaceTrackOrder(playlistId: Long, playlistTrackIds: List<Long>) = Unit
}
