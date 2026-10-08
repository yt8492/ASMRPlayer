package com.yt8492.asmrplayer.ui.common

import android.content.Intent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yt8492.asmrplayer.playback.Media3PlaybackConnection
import com.yt8492.asmrplayer.service.PlaybackService
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackConnectionProviderTest {
    @get:Rule val composeRule = createComposeRule()
    @Volatile private var connection: Media3PlaybackConnection? = null

    @After fun stopTestService() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.stopService(Intent(context, PlaybackService::class.java))
    }

    @Test fun appServiceConnectionIsSharedAcrossObserversAndReleasedWithComposition() {
        val visible = mutableStateOf(true)
        composeRule.setContent {
            if (visible.value) {
                PlaybackConnectionProvider {
                    val shared = LocalPlaybackConnection.current
                    rememberCurrentPlaybackTrackId()
                    MiniPlaybackController(onOpenPlayer = {})
                    SideEffect { connection = shared }
                }
            }
        }
        composeRule.waitUntil(15_000) { connection?.state?.value?.controller != null }
        val connected = requireNotNull(connection)
        assertFalse(connected.state.value.connectionFailed)
        composeRule.runOnIdle { visible.value = false }
        composeRule.waitForIdle()
        assertNull(connected.state.value.controller)
        assertNull(connected.state.value.mediaItem)
    }
}
