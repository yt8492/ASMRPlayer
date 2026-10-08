package com.yt8492.asmrplayer.ui.fileexplorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yt8492.asmrplayer.R
import com.yt8492.asmrplayer.data.model.BrowsableDirectory
import com.yt8492.asmrplayer.data.model.DocumentFile
import com.yt8492.asmrplayer.data.model.DocumentKind
import com.yt8492.asmrplayer.data.model.ImageFile
import com.yt8492.asmrplayer.data.model.Track
import com.yt8492.asmrplayer.ui.common.SingleLineMarqueeText
import com.yt8492.asmrplayer.ui.common.formatDuration

@Composable
internal fun EmptyFileExplorer(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = R.string.file_explorer_empty),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(text = stringResource(id = R.string.file_explorer_library_settings_description))
        TextButton(onClick = onRetry) {
            Text(text = stringResource(id = R.string.common_retry))
        }
    }
}

@Composable
internal fun FileExplorerList(
    directories: List<BrowsableDirectory>,
    tracks: List<Track>,
    images: List<ImageFile>,
    documents: List<DocumentFile>,
    onDirectoryClick: (String) -> Unit,
    onFolderPermissionRequired: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onImageClick: (ImageFile) -> Unit,
    onDocumentClick: (DocumentFile) -> Unit,
    onAddToPlaylistClick: (Track) -> Unit,
    onAddDirectoryToPlaylistClick: (BrowsableDirectory) -> Unit,
    currentPlaybackTrackId: Long?,
    resetRequestKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(resetRequestKey) {
        if (resetRequestKey > 0) {
            listState.scrollToItem(0)
        }
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
    ) {
        items(
            items = directories,
            key = { it.path },
        ) { directory ->
            ListItem(
                modifier = Modifier.clickable {
                    if (directory.hasPermission) onDirectoryClick(directory.path) else onFolderPermissionRequired()
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = stringResource(id = R.string.file_explorer_folder_content_description),
                    )
                },
                headlineContent = {
                    SingleLineMarqueeText(
                        text = directory.name,
                    )
                },
                supportingContent = {
                    Text(if (directory.hasPermission) {
                        stringResource(id = R.string.file_explorer_item_count, directory.itemCount)
                    } else {
                        "アクセス許可が必要です。タップして設定を開く"
                    })
                },
                trailingContent = {
                    IconButton(onClick = { onAddDirectoryToPlaylistClick(directory) }, enabled = directory.hasPermission) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = stringResource(id = R.string.playlist_add_folder),
                        )
                    }
                },
            )
            HorizontalDivider()
        }
        itemsIndexed(
            items = tracks,
            key = { _, track -> track.id },
        ) { index, track ->
            val isCurrentTrack = track.id == currentPlaybackTrackId
            val contentColor = if (isCurrentTrack) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            ListItem(
                modifier = Modifier.combinedClickable(
                    onClick = { onTrackClick(index) },
                    onLongClickLabel = stringResource(id = R.string.track_info_show),
                    onLongClick = { onTrackLongClick(track) },
                ),
                leadingContent = {
                    Icon(
                        imageVector = if (isCurrentTrack) Icons.Filled.PlayArrow else Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = if (isCurrentTrack) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                headlineContent = {
                    SingleLineMarqueeText(
                        text = track.title,
                        color = contentColor,
                    )
                },
                supportingContent = {
                    SingleLineMarqueeText(
                        text = track.artist,
                        color = if (isCurrentTrack) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                trailingContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatDuration(track.durationMs),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        IconButton(onClick = { onAddToPlaylistClick(track) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = stringResource(id = R.string.playlist_add_track),
                            )
                        }
                    }
                },
            )
            HorizontalDivider()
        }
        items(
            items = images,
            key = { image -> "image-${image.id}" },
        ) { image ->
            ListItem(
                modifier = Modifier.clickable { onImageClick(image) },
                leadingContent = {
                    AsyncImage(
                        model = image.uri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop,
                        placeholder = rememberVectorPainter(Icons.Filled.Image),
                        error = rememberVectorPainter(Icons.Filled.Image),
                        fallback = rememberVectorPainter(Icons.Filled.Image),
                    )
                },
                headlineContent = {
                    SingleLineMarqueeText(
                        text = image.title,
                    )
                },
                supportingContent = {
                    Text(
                        text = image.mimeType.ifEmpty { stringResource(id = R.string.file_explorer_image_preview) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            HorizontalDivider()
        }
        items(documents, key = { "document-${it.id}" }) { document ->
            ListItem(
                modifier = Modifier.clickable { onDocumentClick(document) },
                leadingContent = {
                    Icon(if (document.kind == DocumentKind.PDF) Icons.Filled.PictureAsPdf else Icons.Filled.Description,
                        contentDescription = null)
                },
                headlineContent = { SingleLineMarqueeText(document.name) },
                supportingContent = { Text(if (document.kind == DocumentKind.PDF) "PDF" else "txt") },
            )
            HorizontalDivider()
        }
    }
}
