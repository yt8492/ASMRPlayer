package com.yt8492.asmrplayer.ui.player

import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.yt8492.asmrplayer.R
import com.yt8492.asmrplayer.playback.loop.ABLoopButtonState
import com.yt8492.asmrplayer.playback.loop.TrackLoopRangeFactory
import com.yt8492.asmrplayer.playback.model.PlaybackQueue
import com.yt8492.asmrplayer.ui.common.formatDuration

@Composable
internal fun FixedSeekPanel(
    positionMs: Long,
    durationMs: Long,
    loopStartMs: Long?,
    loopEndMs: Long?,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(PlayerFixedSeekPanelHeight)
            .background(MaterialTheme.colorScheme.background),
    ) {
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 4.dp,
                    bottom = 2.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ABLoopSlider(
                positionMs = positionMs,
                durationMs = durationMs,
                startMs = loopStartMs,
                endMs = loopEndMs,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDuration(positionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatDuration(durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun RepeatModeButton(
    repeatMode: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = repeatMode != Player.REPEAT_MODE_OFF
    val contentDescription = when (repeatMode) {
        Player.REPEAT_MODE_ONE -> stringResource(id = R.string.player_repeat_one)
        Player.REPEAT_MODE_ALL -> stringResource(id = R.string.player_repeat_all)
        else -> stringResource(id = R.string.player_repeat_off)
    }
    val tint = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    IconButton(
        onClick = onClick,
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        },
    ) {
        Icon(
            imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) {
                Icons.Filled.RepeatOne
            } else {
                Icons.Filled.Repeat
            },
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .width(32.dp)
                .height(32.dp),
        )
    }
}

@Composable
internal fun ABLoopSlider(
    positionMs: Long,
    durationMs: Long,
    startMs: Long?,
    endMs: Long?,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val startMarkerColor = MaterialTheme.colorScheme.primary
    val endMarkerColor = MaterialTheme.colorScheme.tertiary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value = positionMs.coerceAtLeast(0L).toFloat(),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawABLoopMarker(
                label = "A",
                positionMs = startMs,
                durationMs = durationMs,
                color = startMarkerColor,
            )
            drawABLoopMarker(
                label = "B",
                positionMs = endMs,
                durationMs = durationMs,
                color = endMarkerColor,
            )
        }
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawABLoopMarker(
    label: String,
    positionMs: Long?,
    durationMs: Long,
    color: Color,
) {
    if (positionMs == null || durationMs <= 0L) return
    val fraction = positionMs.coerceIn(0L, durationMs).toFloat() / durationMs.toFloat()
    val x = size.width * fraction
    val markerTop = size.height * 0.18f
    val markerBottom = size.height * 0.82f
    drawLine(
        color = color,
        start = Offset(x = x, y = markerTop),
        end = Offset(x = x, y = markerBottom),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round,
    )

    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11.dp.toPx()
        this.color = color.toArgb()
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    drawContext.canvas.nativeCanvas.drawText(
        label,
        x.coerceIn(10.dp.toPx(), size.width - 10.dp.toPx()),
        12.dp.toPx(),
        labelPaint,
    )
}

@Composable
internal fun TrackLoopStatusText(
    startMs: Long?,
    endMs: Long?,
    durationMs: Long,
    isLooping: Boolean,
    modifier: Modifier = Modifier,
) {
    val loopRange = TrackLoopRangeFactory.create(startMs, endMs, durationMs)
    val text = when {
        isLooping && loopRange != null -> stringResource(
            id = R.string.player_loop_active_range,
            formatDuration(loopRange.startMs),
            formatDuration(loopRange.endMs),
        )

        loopRange != null -> stringResource(
            id = R.string.player_loop_range,
            formatDuration(loopRange.startMs),
            formatDuration(loopRange.endMs),
        )

        startMs != null -> stringResource(id = R.string.player_loop_start_point, formatDuration(startMs))
        else -> stringResource(id = R.string.player_loop_not_set)
    }
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ABLoopButton(
    state: ABLoopButtonState,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loopRange = TrackLoopRangeFactory.create(state.startMs, state.endMs, Long.MAX_VALUE)
    val clickLabel = when {
        state.isLooping -> stringResource(id = R.string.player_loop_stop)
        loopRange != null -> stringResource(id = R.string.player_loop_start)
        state.startMs != null -> stringResource(id = R.string.player_loop_set_end_and_start)
        else -> stringResource(id = R.string.player_loop_set_start)
    }
    val containerColor = if (state.isLooping) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (state.isLooping) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(containerColor)
            .semantics { contentDescription = clickLabel }
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = clickLabel,
                onLongClickLabel = stringResource(id = R.string.player_loop_clear),
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(id = R.string.player_loop_button_label),
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
internal fun SeekFeedbackBadge(
    feedback: SeekFeedback,
    modifier: Modifier = Modifier,
) {
    Text(
        text = feedback.label,
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.large,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
internal fun AlbumArt(
    albumArtUri: Uri?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = albumArtUri,
        contentDescription = contentDescription,
        modifier = modifier
            .aspectRatio(4f / 3f)
            .fillMaxWidth(),
        contentScale = ContentScale.Fit,
        placeholder = rememberVectorPainter(Icons.Filled.Album),
        error = rememberVectorPainter(Icons.Filled.Album),
        fallback = rememberVectorPainter(Icons.Filled.Album),
    )
}

internal fun Player.seekRelative(offsetMs: Long) {
    val currentPositionMs = currentPosition.coerceAtLeast(0L)
    val targetPositionMs = currentPositionMs + offsetMs
    val durationMs = duration
    val clampedPositionMs = if (durationMs > 0) {
        targetPositionMs.coerceIn(0L, durationMs)
    } else {
        targetPositionMs.coerceAtLeast(0L)
    }
    seekTo(clampedPositionMs)
}

internal fun Int.nextRepeatMode(): Int = when (this) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
    else -> Player.REPEAT_MODE_OFF
}

internal const val DOUBLE_TAP_SEEK_INTERVAL_MS = 10_000L
internal const val SEEK_FEEDBACK_VISIBLE_MS = 600L
internal val PlayerFixedSeekPanelHeight = 72.dp

internal enum class SeekFeedback(
    val label: String,
    val alignment: Alignment,
) {
    Backward("-10秒", Alignment.CenterStart),
    Forward("+10秒", Alignment.CenterEnd),
}

@Composable
internal fun PlaybackQueue.artworkLabel(): String? {
    return when (this) {
        is PlaybackQueue.Playlist -> stringResource(id = R.string.player_artwork_scope_playlist)
        is PlaybackQueue.Folder -> stringResource(id = R.string.player_artwork_scope_folder)
    }
}

internal sealed interface ArtworkPickerTarget {
    data class Track(val trackId: Long) : ArtworkPickerTarget
    data object Queue : ArtworkPickerTarget
}
