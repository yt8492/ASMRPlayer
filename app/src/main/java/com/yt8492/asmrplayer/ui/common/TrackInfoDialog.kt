package com.yt8492.asmrplayer.ui.common

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yt8492.asmrplayer.R
import com.yt8492.asmrplayer.data.model.Track

@Composable
fun TrackInfoDialog(
    track: Track,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val unknown = stringResource(id = R.string.track_info_unknown)
    val fileSize = track.fileSizeBytes
        ?.takeIf { it >= 0L }
        ?.let { Formatter.formatFileSize(context, it) }
        ?: unknown
    val duration = track.durationMs
        .takeIf { it >= 0L }
        ?.let(::formatTrackDuration)
        ?: unknown

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(id = R.string.track_info_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TrackInfoField(
                    label = stringResource(id = R.string.track_info_track_title),
                    value = track.title.ifBlank { unknown },
                )
                TrackInfoField(
                    label = stringResource(id = R.string.track_info_file_size),
                    value = fileSize,
                )
                TrackInfoField(
                    label = stringResource(id = R.string.track_info_duration),
                    value = duration,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.common_close))
            }
        },
    )
}

@Composable
private fun TrackInfoField(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

internal fun formatTrackDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
