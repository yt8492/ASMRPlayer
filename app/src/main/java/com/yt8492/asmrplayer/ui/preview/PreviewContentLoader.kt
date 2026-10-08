package com.yt8492.asmrplayer.ui.preview

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.yt8492.asmrplayer.data.model.DocumentKind
import java.io.IOException
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking

/** ファイル読込とテキスト組版。呼び出し元の直列executor上で実行する。 */
internal class PreviewContentLoader(private val context: Context, private val imageLoadJob: Job, private val pdf: PdfPreviewRenderer) {
    fun load(file: PreviewFile, encoding: TextEncoding, viewWidth: Int, checkActive: () -> Unit,
        onTextInfo: (Long, TextEncoding?) -> Unit): PreviewScene {
        val uri = Uri.parse(file.uri)
        return when (file.kind) {
                    null -> {
                        val result = runBlocking(imageLoadJob) {
                            context.imageLoader.execute(ImageRequest.Builder(context).data(uri)
                                .size(4096).allowHardware(false).build())
                        }
                        if (result !is SuccessResult) throw IOException("画像を読み込めません。")
                        PreviewScene.Image(result.drawable.toBitmap())
                    }
                    DocumentKind.TEXT -> {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                            readPreviewText(stream) { checkActive() }
                        } ?: throw IOException("ファイルを開けません。")
                        onTextInfo(bytes.size.toLong(), null)
                        val decoded = decodePreviewText(bytes, encoding)
                        onTextInfo(bytes.size.toLong(), decoded.encoding)
                        val text = decoded.text
                        if (text.isEmpty()) throw IOException("このtxtファイルは空です。")
                        val padding = 16 * context.resources.displayMetrics.density
                        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.BLACK
                            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, context.resources.displayMetrics)
                        }
                        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint,
                            max(1, (viewWidth - 2 * padding).toInt()))
                            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(true)
                            .setLineSpacing(0f, 1.25f).build()
                        PreviewScene.Text(layout, padding)
                    }
            DocumentKind.PDF -> PreviewScene.Pdf(pdf.load(uri, checkActive))
        }
    }
}
