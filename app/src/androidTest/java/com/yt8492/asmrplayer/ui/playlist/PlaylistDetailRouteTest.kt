package com.yt8492.asmrplayer.ui.playlist

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yt8492.asmrplayer.data.local.AppDatabase
import com.yt8492.asmrplayer.data.model.Track
import com.yt8492.asmrplayer.data.repository.PlaylistRepositoryImpl
import com.yt8492.asmrplayer.data.repository.TrackRepository
import com.yt8492.asmrplayer.data.repository.documentTrackId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDetailRouteTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun 音声権限なしでプレイリストを読み込み選択した曲を再生へ渡せる() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(permission))
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val store = ViewModelStore()
        try {
            val playlistRepository = PlaylistRepositoryImpl(database.playlistDao())
            val playlistId = playlistRepository.createPlaylist("選択フォルダの曲")
            val track = Track(
                id = documentTrackId(1), title = "選択した音声", artist = "",
                durationMs = 1_000, fileSizeBytes = 100, trackNumber = 0,
                uri = Uri.parse("content://test.documents/tree/root/document/audio"),
            )
            playlistRepository.addTrack(playlistId, track.id)
            val trackRepository = object : TrackRepository {
                override suspend fun getTracks(trackIds: List<Long>) = trackIds.mapNotNull {
                    track.takeIf { track -> track.id == it }
                }

                override suspend fun getTracksInDirectory(directoryPath: String): List<Track> = emptyList()
            }
            val viewModel = PlaylistDetailViewModel(playlistId, playlistRepository, trackRepository)
            store.put("playlist", viewModel)
            var selectedTracks = emptyList<Track>()
            var selectedIndex: Int? = null
            composeRule.setContent {
                MaterialTheme {
                    PlaylistDetailRoute(
                        playlistId = playlistId,
                        onBack = {},
                        onTrackClick = { items, index ->
                            selectedTracks = items.map { it.track }
                            selectedIndex = index
                        },
                        viewModel = viewModel,
                    )
                }
            }
            composeRule.waitUntil(5_000) { !viewModel.uiState.value.isLoading }
            composeRule.onNodeWithText(track.title).assertIsDisplayed().performClick()
            composeRule.runOnIdle {
                assertEquals(listOf(track), selectedTracks)
                assertEquals(0, selectedIndex)
            }
        } finally {
            composeRule.runOnIdle { store.clear() }
            database.close()
        }
    }
}
