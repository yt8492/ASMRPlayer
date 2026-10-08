package com.yt8492.asmrplayer.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yt8492.asmrplayer.playback.Media3PlaybackConnection
import com.yt8492.asmrplayer.playback.PlaybackConnectionState

internal val LocalPlaybackConnection = staticCompositionLocalOf<Media3PlaybackConnection?> { null }

@Composable
internal fun PlaybackConnectionProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val connection = remember(context.applicationContext) { Media3PlaybackConnection.connect(context) }
    DisposableEffect(connection) { onDispose { connection.close() } }
    CompositionLocalProvider(LocalPlaybackConnection provides connection, content = content)
}

@Composable
internal fun rememberPlaybackConnectionState(): State<PlaybackConnectionState> {
    val connection = LocalPlaybackConnection.current
    return connection?.state?.collectAsStateWithLifecycle() ?: remember { derivedStateOf { PlaybackConnectionState() } }
}

@Composable
fun rememberCurrentPlaybackTrackId(): State<Long?> {
    val state = rememberPlaybackConnectionState()
    return remember(state) { derivedStateOf { state.value.mediaItem?.mediaId?.toLongOrNull() } }
}
