package com.yt8492.asmrplayer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yt8492.asmrplayer.R
import com.yt8492.asmrplayer.di.appContainer
import com.yt8492.asmrplayer.playback.model.PlaybackRequest
import com.yt8492.asmrplayer.playback.model.artworkTarget
import com.yt8492.asmrplayer.playback.toPlaybackRequest

@Composable
fun MiniPlaybackController(
    onOpenPlayer: (PlaybackRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val connection by rememberPlaybackConnectionState()
    val controller = connection.controller ?: return
    val item = connection.mediaItem ?: return
    val request = item.toPlaybackRequest(connection.index) ?: return
    val title = item.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: return
    val artwork = LocalContext.current.appContainer().artwork
    val artworkFlow = remember(artwork, request.queue, request.trackId, item.mediaMetadata.artworkUri) {
        artwork.observeResolvedArtwork(request.trackId, request.queue.artworkTarget(), item.mediaMetadata.artworkUri)
    }
    val artworkUri by artworkFlow.collectAsStateWithLifecycle(initialValue = item.mediaMetadata.artworkUri)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MiniPlaybackControllerHeight)
                .clickable { onOpenPlayer(request) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = artworkUri,
                contentDescription = title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
                placeholder = rememberVectorPainter(Icons.Filled.MusicNote),
                error = rememberVectorPainter(Icons.Filled.MusicNote),
                fallback = rememberVectorPainter(Icons.Filled.MusicNote),
            )
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = {
                    if (connection.isPlaying) {
                        controller.pause()
                    } else {
                        controller.play()
                    }
                },
            ) {
                Icon(
                    imageVector = if (connection.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (connection.isPlaying) {
                        stringResource(id = R.string.player_pause)
                    } else {
                        stringResource(id = R.string.player_play)
                    },
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        HorizontalDivider()
    }
}

private val MiniPlaybackControllerHeight = 72.dp
