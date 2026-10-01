package com.yt8492.asmrplayer

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yt8492.asmrplayer.navigation.AppNavHost
import com.yt8492.asmrplayer.navigation.PlaybackDestination
import com.yt8492.asmrplayer.service.PlaybackService
import com.yt8492.asmrplayer.ui.theme.ASMRPlayerTheme

class MainActivity : ComponentActivity() {
    private var playbackDestination by mutableStateOf<PlaybackDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playbackDestination = intent.toPlaybackDestination()
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
        playbackDestination = intent.toPlaybackDestination()
    }

    private fun Intent.toPlaybackDestination(): PlaybackDestination? {
        if (action != PlaybackService.ACTION_OPEN_PLAYER) return null
        val queueType = getStringExtra(PlaybackService.EXTRA_QUEUE_TYPE)
            ?.takeIf { it == PlaybackService.QUEUE_TYPE_PLAYLIST || it == PlaybackService.QUEUE_TYPE_FOLDER }
            ?: return null
        val playlistId = getLongExtra(PlaybackService.EXTRA_PLAYLIST_ID, -1L)
        val trackId = getLongExtra(PlaybackService.EXTRA_TRACK_ID, -1L).takeIf { it >= 0 } ?: return null
        val startIndex = getIntExtra(PlaybackService.EXTRA_START_INDEX, -1).takeIf { it >= 0 }
        val playlistName = getStringExtra(PlaybackService.EXTRA_PLAYLIST_NAME).orEmpty()
        val folderPath = getStringExtra(PlaybackService.EXTRA_FOLDER_PATH).orEmpty()
        val folderTitle = getStringExtra(PlaybackService.EXTRA_FOLDER_TITLE).orEmpty()
        if (queueType == PlaybackService.QUEUE_TYPE_PLAYLIST && playlistId < 0) return null
        return PlaybackDestination(
            queueType = queueType,
            playlistId = playlistId,
            trackId = trackId,
            startIndex = startIndex,
            playlistName = playlistName,
            folderPath = folderPath,
            folderTitle = folderTitle,
            requestId = SystemClock.elapsedRealtime(),
        )
    }
}
