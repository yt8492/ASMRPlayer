package com.yt8492.asmrplayer.ui.fileexplorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yt8492.asmrplayer.R
import com.yt8492.asmrplayer.data.model.Playlist
import com.yt8492.asmrplayer.ui.common.SingleLineMarqueeText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistPickerSheet(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(id = R.string.playlist_select_title),
                style = MaterialTheme.typography.titleMedium,
            )
            FilledTonalButton(
                onClick = onCreatePlaylist,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                )
                Text(text = stringResource(id = R.string.playlist_create))
            }
            if (playlists.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.playlist_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                playlists.forEach { playlist ->
                    ListItem(
                        modifier = Modifier.clickable { onPlaylistClick(playlist) },
                        headlineContent = {
                            SingleLineMarqueeText(
                                text = playlist.name,
                            )
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.playlist_track_count, playlist.trackCount))
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
