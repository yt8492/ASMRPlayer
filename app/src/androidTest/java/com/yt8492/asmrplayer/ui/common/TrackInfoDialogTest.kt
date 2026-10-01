package com.yt8492.asmrplayer.ui.common

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yt8492.asmrplayer.data.model.Playlist
import com.yt8492.asmrplayer.data.model.Track
import com.yt8492.asmrplayer.ui.fileexplorer.FileExplorerScreen
import com.yt8492.asmrplayer.ui.fileexplorer.FileExplorerUiState
import com.yt8492.asmrplayer.ui.playlist.PlaylistDetailScreen
import com.yt8492.asmrplayer.ui.playlist.PlaylistDetailUiState
import com.yt8492.asmrplayer.ui.playlist.PlaylistTrackItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackInfoDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ダイアログに長いタイトルとファイル情報を表示して閉じられる() {
        val track = createTrack()
        val expectedFileSize = Formatter.formatFileSize(
            ApplicationProvider.getApplicationContext(),
            track.fileSizeBytes!!,
        )
        composeRule.setContent {
            var isVisible by remember { mutableStateOf(true) }
            MaterialTheme {
                if (isVisible) {
                    TrackInfoDialog(
                        track = track,
                        onDismiss = { isVisible = false },
                    )
                }
            }
        }

        composeRule.onNodeWithText(track.title).assertIsDisplayed()
        composeRule.onNodeWithText(expectedFileSize).assertIsDisplayed()
        composeRule.onNodeWithText("1:02:03").assertIsDisplayed()
        composeRule.onNodeWithText("閉じる").performClick()

        composeRule.onAllNodesWithText("トラック情報").assertCountEquals(0)
    }

    @Test
    fun ファイルサイズを取得できない場合は不明と表示する() {
        val track = createTrack().copy(fileSizeBytes = null)
        composeRule.setContent {
            MaterialTheme {
                TrackInfoDialog(
                    track = track,
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("不明").assertIsDisplayed()
    }

    @Test
    fun ファイル一覧は長押しで情報を表示して通常タップも維持する() {
        val track = createTrack()
        var clickedIndex: Int? = null
        composeRule.setContent {
            MaterialTheme {
                FileExplorerScreen(
                    uiState = FileExplorerUiState(
                        currentPath = "Music/Test/",
                        tracks = listOf(track),
                    ),
                    onRetry = {},
                    onRefresh = {},
                    onDirectoryClick = {},
                    onBack = {},
                    onTrackClick = { clickedIndex = it },
                    onAddTrackToPlaylist = { _, _ -> },
                    onCreatePlaylistAndAddTrack = { _, _ -> },
                    onCreatePlaylistFromDirectory = { _, _ -> },
                    onAddDirectoryToPlaylist = { _, _ -> },
                    onErrorShown = {},
                    onPlaylistMessageShown = {},
                )
            }
        }

        composeRule.onNodeWithText(track.title).performTouchInput { longClick() }

        composeRule.onNodeWithText("トラック情報").assertIsDisplayed()
        composeRule.runOnIdle { assertNull(clickedIndex) }
        composeRule.onNodeWithText("閉じる").performClick()
        composeRule.onNodeWithText(track.title).performClick()
        composeRule.runOnIdle { assertEquals(0, clickedIndex) }
    }

    @Test
    fun プレイリスト詳細は長押しで情報を表示して通常タップも維持する() {
        val track = createTrack()
        var clickedIndex: Int? = null
        composeRule.setContent {
            MaterialTheme {
                PlaylistDetailScreen(
                    uiState = PlaylistDetailUiState(
                        isLoading = false,
                        playlist = Playlist(
                            id = 1L,
                            name = "テストプレイリスト",
                            trackCount = 1,
                            createdAt = 0L,
                            updatedAt = 0L,
                        ),
                        playlistTracks = listOf(
                            PlaylistTrackItem(
                                playlistTrackId = 10L,
                                track = track,
                            ),
                        ),
                    ),
                    onBack = {},
                    onTrackClick = { clickedIndex = it },
                    onRenamePlaylist = {},
                    onRemoveTrack = {},
                    onMoveTrack = { _, _ -> },
                    onDragFinished = {},
                    onToggleEditMode = {},
                    onErrorShown = {},
                )
            }
        }

        composeRule.onNodeWithText(track.title).performTouchInput { longClick() }

        composeRule.onNodeWithText("トラック情報").assertIsDisplayed()
        composeRule.runOnIdle { assertNull(clickedIndex) }
        composeRule.onNodeWithText("閉じる").performClick()
        composeRule.onNodeWithText(track.title).performClick()
        composeRule.runOnIdle { assertEquals(0, clickedIndex) }
    }

    private fun createTrack(): Track {
        return Track(
            id = 1L,
            title = "一覧では最後まで表示できないほど長いトラックタイトルをダイアログで全文確認するためのサンプル",
            artist = "アーティスト",
            durationMs = 3_723_000L,
            fileSizeBytes = 12_345_678L,
            trackNumber = 1,
            uri = Uri.EMPTY,
        )
    }
}
