package com.yt8492.asmrplayer.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yt8492.asmrplayer.data.model.LibraryFolder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAccessRecoveryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun 再許可待ちでは再読み込みの代わりに許可ボタンを表示し復旧後は読み込める() {
        val folder = LibraryFolder("content://test/tree/root", "作品", 2, 1, 0, null, hasPermission = false)
        val state = mutableStateOf(SettingsUiState(folders = listOf(folder)))
        var requested: LibraryFolder? = null
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = state.value,
                    isInitialSetup = true,
                    hasAudioPermission = false,
                    onRequestAudioPermission = {},
                    onOpenAppSettings = {},
                    onAddFolder = {},
                    onReloadFolder = {},
                    onRestoreFolderAccess = { requested = it },
                    onRemoveFolder = {},
                    onReloadAll = {},
                    onCompleteSetup = {},
                    onMessageShown = {},
                )
            }
        }
        composeRule.onNodeWithText("ライブラリを再読み込み").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("再読み込み").assertDoesNotExist()
        composeRule.onNodeWithText("アクセスを許可").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(folder, requested)
            state.value = SettingsUiState(folders = listOf(folder.copy(hasPermission = true)))
        }
        composeRule.onNodeWithText("アクセスを許可").assertDoesNotExist()
        composeRule.onNodeWithText("フォルダへのアクセスを許可してください").assertDoesNotExist()
        composeRule.onNodeWithText("再読み込み").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithText("ライブラリを再読み込み").performScrollTo().assertIsEnabled()
    }
}
