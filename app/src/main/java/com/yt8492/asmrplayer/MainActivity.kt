package com.yt8492.asmrplayer

import android.content.Intent
import android.net.Uri
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
        val albumId = getLongExtra(PlaybackService.EXTRA_ALBUM_ID, -1L).takeIf { it >= 0 } ?: return null
        val trackId = getLongExtra(PlaybackService.EXTRA_TRACK_ID, -1L).takeIf { it >= 0 } ?: return null
        val albumTitle = getStringExtra(PlaybackService.EXTRA_ALBUM_TITLE).orEmpty()
        val albumArtUri = getStringExtra(PlaybackService.EXTRA_ALBUM_ART_URI)
            ?.takeIf { it.isNotEmpty() }
            ?.let { Uri.parse(it) }
        return PlaybackDestination(
            albumId = albumId,
            trackId = trackId,
            albumTitle = albumTitle,
            albumArtUri = albumArtUri,
            requestId = SystemClock.elapsedRealtime(),
        )
    }
}
