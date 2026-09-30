package com.yt8492.asmrplayer.ui.fileexplorer

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yt8492.asmrplayer.data.model.AudioDirectory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileExplorerRefreshTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun 再許可待ちのフォルダは読み込まず設定画面へ案内する() {
        var settingsOpened = 0
        var directoriesOpened = 0
        composeRule.setContent {
            MaterialTheme {
                FileExplorerScreen(
                    uiState = FileExplorerUiState(directories = listOf(AudioDirectory("saved/", "復元された作品", 2, hasPermission = false))),
                    onOpenSettings = { settingsOpened += 1 },
                    onRetry = {},
                    onRefresh = {},
                    onDirectoryClick = { directoriesOpened += 1 },
                    onBack = {},
                    onTrackClick = {},
                    onAddTrackToPlaylist = { _, _ -> },
                    onCreatePlaylistAndAddTrack = { _, _ -> },
                    onCreatePlaylistFromDirectory = { _, _ -> },
                    onAddDirectoryToPlaylist = { _, _ -> },
                    onErrorShown = {},
                    onPlaylistMessageShown = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("フォルダをプレイリストに追加").assertIsNotEnabled()
        composeRule.onNodeWithText("復元された作品").performClick()
        composeRule.runOnIdle {
            assertEquals(1, settingsOpened)
            assertEquals(0, directoriesOpened)
        }
    }

    @Test
    fun 空のファイル一覧を下にスワイプすると再読み込みする() {
        var refreshCount = 0
        composeRule.setContent {
            MaterialTheme {
                FileExplorerScreen(
                    uiState = FileExplorerUiState(currentPath = "Music/Test/"),
                    onRetry = {},
                    onRefresh = { refreshCount += 1 },
                    onDirectoryClick = {},
                    onBack = {},
                    onTrackClick = {},
                    onAddTrackToPlaylist = { _, _ -> },
                    onCreatePlaylistAndAddTrack = { _, _ -> },
                    onCreatePlaylistFromDirectory = { _, _ -> },
                    onAddDirectoryToPlaylist = { _, _ -> },
                    onErrorShown = {},
                    onPlaylistMessageShown = {},
                )
            }
        }

        composeRule.onNodeWithTag(FILE_EXPLORER_PULL_TO_REFRESH_TAG)
            .performTouchInput { swipeDown() }

        composeRule.runOnIdle {
            assertEquals(1, refreshCount)
        }
    }
}
