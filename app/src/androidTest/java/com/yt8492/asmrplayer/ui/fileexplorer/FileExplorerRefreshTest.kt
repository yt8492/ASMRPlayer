package com.yt8492.asmrplayer.ui.fileexplorer

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileExplorerRefreshTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun 空のファイル一覧を下にスワイプすると再読み込みする() {
        var refreshCount = 0
        composeRule.setContent {
            MaterialTheme {
                FileExplorerScreen(
                    uiState = FileExplorerUiState(currentPath = "Music/Test/"),
                    hasPermission = true,
                    hasMissingPermission = false,
                    onRequestPermission = {},
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
