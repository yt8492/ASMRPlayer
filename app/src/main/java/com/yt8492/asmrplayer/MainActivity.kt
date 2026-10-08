package com.yt8492.asmrplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yt8492.asmrplayer.navigation.AppNavHost
import com.yt8492.asmrplayer.playback.model.PlaybackRequest
import com.yt8492.asmrplayer.playback.toPlaybackRequest
import com.yt8492.asmrplayer.ui.theme.ASMRPlayerTheme

class MainActivity : ComponentActivity() {
    private var playbackDestination by mutableStateOf<PlaybackRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playbackDestination = intent.toPlaybackRequest()
        enableEdgeToEdge()
        setContent {
            ASMRPlayerTheme {
                AppNavHost(playbackDestination = playbackDestination)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        playbackDestination = intent.toPlaybackRequest()
    }

}
