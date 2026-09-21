package com.yt8492.asmrplayer.ui.preview

import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.yt8492.asmrplayer.data.model.DocumentFile
import com.yt8492.asmrplayer.data.model.DocumentKind
import com.yt8492.asmrplayer.ui.fileexplorer.FileExplorerScreen
import com.yt8492.asmrplayer.ui.fileexplorer.FileExplorerUiState
import java.io.File
import org.junit.After
import org.junit.Rule
import org.junit.Test

class FilePreviewDialogTest {
    @get:Rule val rule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = File.createTempFile("preview-dialog", ".txt", context.cacheDir).apply {
        writeText("日本語の説明です。\n画像・PDFと同じ操作で拡大できます。")
    }
    @After fun cleanUp() { file.delete() }

    @Test fun 文書だけの一覧から開き状態復元と文字コード切替と連続開閉ができる() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            MaterialTheme {
                FileExplorerScreen(
                    uiState = FileExplorerUiState(documents = listOf(
                        DocumentFile(123, "説明.txt", Uri.fromFile(file), DocumentKind.TEXT, file.length()),
                    )),
                    onRetry = {}, onRefresh = {}, onDirectoryClick = {}, onBack = {}, onTrackClick = {},
                    onAddTrackToPlaylist = { _, _ -> }, onCreatePlaylistAndAddTrack = { _, _ -> },
                    onCreatePlaylistFromDirectory = { _, _ -> }, onAddDirectoryToPlaylist = { _, _ -> },
                    onErrorShown = {}, onPlaylistMessageShown = {},
                )
            }
        }
        rule.onNodeWithText("説明.txt").performClick()
        rule.onNodeWithTag("file-preview").assertIsDisplayed()
        rule.onNodeWithText("100% リセット").assertDoesNotExist()
        rule.onNodeWithText("文字コード").assertDoesNotExist()
        rule.onNodeWithContentDescription("ファイル情報").performClick()
        rule.onNodeWithText("ファイルサイズ").assertIsDisplayed()
        rule.onNodeWithText(Formatter.formatFileSize(context, file.length())).assertIsDisplayed()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("UTF-8（自動判定）").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("読み込み設定：自動").performClick()
        rule.onNodeWithText("UTF-8").performClick()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("読み込み設定：UTF-8").assertIsDisplayed()
        rule.onNodeWithText(Formatter.formatFileSize(context, file.length())).assertIsDisplayed()
        rule.onNodeWithText("閉じる").performClick()
        rule.onNodeWithTag("file-preview").assertIsDisplayed()
        rule.onNodeWithText("文字コード").assertDoesNotExist()
        rule.onNodeWithContentDescription("プレビューを閉じる").performClick()
        repeat(3) {
            rule.onNodeWithText("説明.txt").performClick()
            rule.onNodeWithTag("file-preview").assertIsDisplayed()
            rule.onNodeWithContentDescription("プレビューを閉じる").performClick()
        }
        rule.onNodeWithText("説明.txt").assertIsDisplayed()
    }
}
