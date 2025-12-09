package com.yt8492.asmrplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.yt8492.asmrplayer.ui.theme.ASMRPlayerTheme
import com.yt8492.asmrplayer.ui.album.AlbumListRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ASMRPlayerTheme {
                AlbumListRoute(
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
