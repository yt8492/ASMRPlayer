package com.yt8492.asmrplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yt8492.asmrplayer.R
import com.yt8492.asmrplayer.ui.common.SingleLineMarqueeText
import com.yt8492.asmrplayer.ui.common.formatDuration
import com.yt8492.asmrplayer.ui.common.moveDraggedItem

@Composable
internal fun PlaybackQueueSheetHandle(
    modifier: Modifier = Modifier,
) {
    val queueLabel = stringResource(id = R.string.player_queue)

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .semantics {
                contentDescription = queueLabel
            }
            .padding(horizontal = 32.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)),
        )
        Text(
            text = queueLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
internal fun PlaybackQueueSheet(
    queueItems: List<PlayerQueueItem>,
    currentIndex: Int,
    canChangeQueue: Boolean,
    topPadding: Dp,
    onQueueItemClick: (Int) -> Unit,
    onMoveQueueItem: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = topPadding, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlaybackQueueSheetHandle(
            modifier = Modifier
                .fillMaxWidth()
                .height(PlayerQueueSheetPeekHeight),
        )
        Text(
            text = stringResource(id = R.string.player_queue_count, queueItems.size),
            modifier = Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(
                items = queueItems,
                key = { _, queueItem -> queueItem.queueItemId },
            ) { index, queueItem ->
                PlaybackQueueListItem(
                    queueItem = queueItem,
                    index = index,
                    isCurrent = index == currentIndex,
                    canChangeQueue = canChangeQueue,
                    onClick = { onQueueItemClick(index) },
                    onMoveQueueItem = onMoveQueueItem,
                    lastIndex = queueItems.lastIndex,
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
internal fun PlaybackQueueListItem(
    queueItem: PlayerQueueItem,
    index: Int,
    isCurrent: Boolean,
    canChangeQueue: Boolean,
    onClick: () -> Unit,
    onMoveQueueItem: (fromIndex: Int, toIndex: Int) -> Unit,
    lastIndex: Int,
    modifier: Modifier = Modifier,
) {
    var currentIndex by remember(queueItem.queueItemId) { mutableIntStateOf(index) }
    var draggingQueueItemId by remember { mutableStateOf<Long?>(null) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }
    val latestLastIndex by rememberUpdatedState(lastIndex)
    val itemHeightPx = with(LocalDensity.current) { 72.dp.toPx() }
    val track = queueItem.track

    ListItem(
        modifier = modifier
            .graphicsLayer {
                translationY = if (draggingQueueItemId == queueItem.queueItemId) {
                    draggingOffset
                } else {
                    0f
                }
            }
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            headlineColor = if (isCurrent) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            supportingColor = if (isCurrent) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        leadingContent = {
            if (canChangeQueue) {
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = stringResource(id = R.string.player_queue_reorder),
                    modifier = Modifier.pointerInput(queueItem.queueItemId) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingQueueItemId = queueItem.queueItemId
                                draggingOffset = 0f
                                currentIndex = index
                            },
                            onDragEnd = {
                                draggingQueueItemId = null
                                draggingOffset = 0f
                            },
                            onDragCancel = {
                                draggingQueueItemId = null
                                draggingOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val movement = moveDraggedItem(currentIndex, draggingOffset + dragAmount.y,
                                    itemHeightPx, latestLastIndex, onMoveQueueItem)
                                currentIndex = movement.index
                                draggingOffset = movement.offset
                            },
                        )
                    },
                )
            }
        },
        headlineContent = {
            SingleLineMarqueeText(
                text = track.title,
            )
        },
        supportingContent = {
            SingleLineMarqueeText(
                text = track.artist,
            )
        },
        trailingContent = {
            Text(
                text = if (isCurrent) {
                    stringResource(id = R.string.player_queue_current)
                } else {
                    formatDuration(track.durationMs)
                },
                style = MaterialTheme.typography.labelMedium,
            )
        },
    )
}

internal val PlayerQueueSheetPeekHeight = 72.dp
