package com.yt8492.asmrplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yt8492.asmrplayer.data.model.Album
import com.yt8492.asmrplayer.ui.theme.ASMRPlayerTheme
import com.yt8492.asmrplayer.ui.album.AlbumListRoute
import com.yt8492.asmrplayer.ui.track.TrackListRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ASMRPlayerTheme {
                ASMRPlayerApp()
            }
        }
    }
}

@Composable
private fun ASMRPlayerApp() {
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }

    if (selectedAlbum == null) {
        AlbumListRoute(
            modifier = Modifier.fillMaxSize(),
            onAlbumClick = { album ->
                selectedAlbum = album
            },
        )
    } else {
        val album = selectedAlbum!!
        TrackListRoute(
            modifier = Modifier.fillMaxSize(),
            albumId = album.id,
            albumTitle = album.title,
            onBack = { selectedAlbum = null },
        )
    }
}
