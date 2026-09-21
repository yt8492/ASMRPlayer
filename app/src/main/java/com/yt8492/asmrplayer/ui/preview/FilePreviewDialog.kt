package com.yt8492.asmrplayer.ui.preview

import android.text.format.Formatter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yt8492.asmrplayer.data.model.DocumentKind
import com.yt8492.asmrplayer.ui.common.SingleLineMarqueeText

internal data class PreviewFile(
    val uri: String,
    val name: String,
    val kind: DocumentKind?,
    val sizeBytes: Long? = null,
) {
    companion object {
        val Saver = listSaver<PreviewFile?, String>(
            save = { if (it == null) emptyList() else listOf(it.uri, it.name, it.kind?.name.orEmpty(), it.sizeBytes?.toString().orEmpty()) },
            restore = { if (it.isEmpty()) null else PreviewFile(it[0], it[1], it[2].takeIf(String::isNotEmpty)?.let(DocumentKind::valueOf), it.getOrNull(3)?.toLongOrNull()) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilePreviewDialog(file: PreviewFile, onDismiss: () -> Unit) {
    var encoding by rememberSaveable(file.uri) { mutableStateOf(TextEncoding.AUTO) }
    var showInfo by rememberSaveable(file.uri) { mutableStateOf(false) }
    var textSize by remember(file) { mutableStateOf(file.sizeBytes) }
    var detectedEncoding by remember(file, encoding) { mutableStateOf<TextEncoding?>(null) }
    var loading by remember(file, encoding) { mutableStateOf(true) }
    var error by remember(file, encoding) { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<PreviewCanvasView?>(null) }
    var scale by remember(file, encoding) { mutableFloatStateOf(1f) }
    val isText = file.kind == DocumentKind.TEXT
    val previewBackground = if (isText) MaterialTheme.colorScheme.surface else Color(0xffeeeeee)
    val previewForeground = if (isText) MaterialTheme.colorScheme.onSurface else Color.Black
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag("file-preview"),
            topBar = {
                TopAppBar(
                    title = { SingleLineMarqueeText(file.name) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "プレビューを閉じる") }
                    },
                    actions = {
                        if (isText) {
                            IconButton(onClick = { showInfo = true }) {
                                Icon(Icons.Default.Info, contentDescription = "ファイル情報")
                            }
                        } else {
                            TextButton(onClick = { preview?.resetZoom() }) { Text("${(scale * 100).toInt()}% リセット") }
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Box(Modifier.fillMaxSize().background(previewBackground), contentAlignment = Alignment.Center) {
                    key(file, encoding) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize().testTag("preview-canvas"),
                            factory = { context ->
                                PreviewCanvasView(context, file, encoding,
                                    onStatus = { busy, message -> loading = busy; error = message },
                                    onScale = { scale = it },
                                    onTextInfo = { size, detected -> textSize = size; detectedEncoding = detected },
                                ).also { preview = it }
                            },
                            update = { view ->
                                if (isText) view.updateTextColors(previewBackground.toArgb(), previewForeground.toArgb())
                            },
                            onRelease = { it.dispose(); if (preview === it) preview = null },
                        )
                    }
                    if (loading) CircularProgressIndicator()
                    error?.let { Text(it, color = previewForeground, modifier = Modifier.padding(24.dp)) }
                }
            }
        }
        if (isText && showInfo) {
            TextPreviewInfoDialog(
                sizeBytes = textSize,
                encoding = encoding,
                detectedEncoding = detectedEncoding,
                loading = loading,
                onEncodingChange = { encoding = it },
                onDismiss = { showInfo = false },
            )
        }
    }
}

@Composable
private fun TextPreviewInfoDialog(
    sizeBytes: Long?,
    encoding: TextEncoding,
    detectedEncoding: TextEncoding?,
    loading: Boolean,
    onEncodingChange: (TextEncoding) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val size = sizeBytes?.takeIf { it >= 0 }?.let { Formatter.formatFileSize(context, it) } ?: "不明"
    val charset = detectedEncoding?.let {
        if (encoding == TextEncoding.AUTO) "${it.label}（自動判定）" else it.label
    } ?: if (loading) "確認中…" else "不明"
    var menu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ファイル情報") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ファイルサイズ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(size)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("文字コード", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(charset)
                    Box {
                        TextButton(onClick = { menu = true }) { Text("読み込み設定：${encoding.label}") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            TextEncoding.entries.forEach { item ->
                                DropdownMenuItem(text = { Text(item.label) }, onClick = {
                                    onEncodingChange(item)
                                    menu = false
                                })
                            }
                        }
                    }
                    Text("文字化けする場合は文字コードを変更してください。", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}
