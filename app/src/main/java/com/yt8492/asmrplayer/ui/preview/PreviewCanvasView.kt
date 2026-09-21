package com.yt8492.asmrplayer.ui.preview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.util.LruCache
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import com.yt8492.asmrplayer.data.model.DocumentKind
import java.io.File
import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** UIは座標変換のみを担当し、読込・組版・PDF描画・解放を専用スレッドで直列化する。 */
// ComposeのAndroidViewからのみ生成し、XMLからの生成は行わない。
@SuppressLint("ViewConstructor")
internal class PreviewCanvasView(
    context: Context,
    private val file: PreviewFile,
    private val encoding: TextEncoding,
    private val onStatus: (Boolean, String?) -> Unit,
    private val onScale: (Float) -> Unit,
    private val onTextInfo: (Long, TextEncoding?) -> Unit = { _, _ -> },
) : View(context) {
    internal val transform = PreviewTransform()
    private val executor = Executors.newSingleThreadExecutor()
    private val imageLoadJob = Job()
    @Volatile private var disposed = false
    @Volatile private var revision = 0
    private var scene: Scene? = null
    // PDF関連リソースへのアクセスはexecutorに限定する。
    private var renderer: PdfRenderer? = null
    private var temporaryFile: File? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val imageBounds = RectF()
    private val tileBounds = RectF()
    private val paper = Paint().apply { color = Color.WHITE }
    private val textPaper = Paint().apply { color = Color.WHITE }
    private var textColor = Color.BLACK
    private val pendingTiles = AtomicReference<List<Tile>>(emptyList())
    private val rendering = AtomicBoolean(false)
    private val failedTiles = mutableSetOf<Tile>()
    private val cache = object : LruCache<Tile, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Tile, value: Bitmap) = value.allocationByteCount
    }
    private var requestedTiles = emptyList<Tile>()
    private var pdfTops = emptyList<Float>()

    private sealed interface Scene {
        data class Image(val bitmap: Bitmap) : Scene
        data class Text(val layout: StaticLayout, val padding: Float) : Scene
        data class Pdf(val pages: List<PageSize>) : Scene
    }
    private data class PageSize(val width: Int, val height: Int)
    private data class Tile(val page: Int, val renderWidth: Int, val x: Int, val y: Int)

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            transform.zoom(detector.scaleFactor, detector.focusX, detector.focusY)
            changed()
            return true
        }
    })
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean { performClick(); return true }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            transform.zoom(if (transform.scale > 1f) 1f / transform.scale else 2f, e.x, e.y)
            changed()
            return true
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!scaleDetector.isInProgress && e2.pointerCount == 1) {
                transform.pan(-distanceX, -distanceY)
                changed()
            }
            return true
        }
    })

    init {
        contentDescription = "プレビュー。ピンチで拡大、ドラッグで移動、ダブルタップで倍率切り替え"
        isFocusable = true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    // クリック通知はGestureDetector.onSingleTapUpからperformClickへ委譲する。
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    fun resetZoom() { transform.reset(); changed() }

    // テーマ変更時は再読み込みせず、表示位置と倍率を保ったまま配色だけ更新する。
    fun updateTextColors(backgroundColor: Int, foregroundColor: Int) {
        if (textPaper.color == backgroundColor && textColor == foregroundColor) return
        textPaper.color = backgroundColor
        textColor = foregroundColor
        invalidate()
    }

    private fun changed() { onScale(transform.scale); invalidate() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        transform.viewportWidth = w.toFloat()
        transform.viewportHeight = h.toFloat()
        if (w <= 0 || h <= 0 || disposed) return
        if (scene == null || (file.kind == DocumentKind.TEXT && w != oldw)) load(w)
        else updateGeometry()
    }

    private fun load(viewWidth: Int) {
        val ticket = ++revision
        onStatus(true, null)
        executor.execute {
            try {
                checkActive(ticket)
                val uri = Uri.parse(file.uri)
                val loaded = when (file.kind) {
                    null -> {
                        val result = runBlocking(imageLoadJob) {
                            context.imageLoader.execute(ImageRequest.Builder(context).data(uri)
                                .size(4096).allowHardware(false).build())
                        }
                        if (result !is SuccessResult) throw IOException("画像を読み込めません。")
                        Scene.Image(result.drawable.toBitmap())
                    }
                    DocumentKind.TEXT -> {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                            readPreviewText(stream) { checkActive(ticket) }
                        } ?: throw IOException("ファイルを開けません。")
                        post { if (!disposed && revision == ticket) onTextInfo(bytes.size.toLong(), null) }
                        val decoded = decodePreviewText(bytes, encoding)
                        post { if (!disposed && revision == ticket) onTextInfo(bytes.size.toLong(), decoded.encoding) }
                        val text = decoded.text
                        if (text.isEmpty()) throw IOException("このtxtファイルは空です。")
                        val padding = 16 * resources.displayMetrics.density
                        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.BLACK
                            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
                        }
                        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint,
                            max(1, (viewWidth - 2 * padding).toInt()))
                            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(true)
                            .setLineSpacing(0f, 1.25f).build()
                        Scene.Text(layout, padding)
                    }
                    DocumentKind.PDF -> {
                        closePdf()
                        val pdf = openPdf(uri, ticket)
                        renderer = pdf
                        if (pdf.pageCount == 0) throw IOException("このPDFにはページがありません。")
                        val pages = (0 until pdf.pageCount).map { index ->
                            checkActive(ticket)
                            pdf.openPage(index).use { PageSize(it.width, it.height) }
                        }
                        Scene.Pdf(pages)
                    }
                }
                checkActive(ticket)
                post {
                    if (!disposed && revision == ticket) {
                        scene = loaded
                        updateGeometry()
                        onStatus(false, null)
                    }
                }
            } catch (error: Exception) {
                if (file.kind == DocumentKind.PDF) closePdf()
                post { if (!disposed && revision == ticket) onStatus(false, message(error)) }
            }
        }
    }

    private fun openPdf(uri: Uri, ticket: Int): PdfRenderer {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("ファイルを開けません。")
        try {
            return createRenderer(descriptor)
        } catch (_: IllegalArgumentException) {
            // 一部のDocumentsProviderはシークできないパイプを返す。
        }
        val local = File.createTempFile("preview-", ".pdf", context.cacheDir)
        temporaryFile = local
        context.contentResolver.openInputStream(uri)?.use { input ->
            local.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    checkActive(ticket)
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
        } ?: throw IOException("ファイルを開けません。")
        return createRenderer(ParcelFileDescriptor.open(local, ParcelFileDescriptor.MODE_READ_ONLY))
    }

    private fun createRenderer(descriptor: ParcelFileDescriptor): PdfRenderer = try {
        PdfRenderer(descriptor)
    } catch (error: Exception) {
        descriptor.close()
        if (error is SecurityException) throw IOException("パスワード付き、または保護されたPDFには対応していません。", error)
        throw error
    }

    private fun updateGeometry() {
        transform.contentWidth = width.toFloat()
        transform.contentHeight = when (val content = scene) {
            is Scene.Image -> {
                val ratio = min(width.toFloat() / content.bitmap.width, height.toFloat() / content.bitmap.height)
                transform.contentWidth = content.bitmap.width * ratio
                content.bitmap.height * ratio
            }
            is Scene.Text -> content.layout.height + 2 * content.padding
            is Scene.Pdf -> {
                var top = 0f
                pdfTops = content.pages.map { page ->
                    val result = top
                    top += width.toFloat() * page.height / page.width + PAGE_GAP
                    result
                }
                max(0f, top - PAGE_GAP)
            }
            null -> 0f
        }
        transform.clamp()
        requestedTiles = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (disposed) return
        canvas.save()
        canvas.translate(transform.x, transform.y)
        canvas.scale(transform.scale, transform.scale)
        when (val content = scene) {
            is Scene.Image -> {
                imageBounds.set(0f, 0f, transform.contentWidth, transform.contentHeight)
                canvas.drawBitmap(content.bitmap, null, imageBounds, paint)
            }
            is Scene.Text -> {
                canvas.drawRect(0f, 0f, transform.contentWidth, transform.contentHeight, textPaper)
                canvas.translate(content.padding, content.padding)
                content.layout.paint.color = textColor
                // StaticLayoutはCanvasのクリップ範囲内の行だけを描画する。
                content.layout.draw(canvas)
            }
            is Scene.Pdf -> drawPdf(canvas, content)
            null -> Unit
        }
        canvas.restore()
    }

    private fun drawPdf(canvas: Canvas, content: Scene.Pdf) {
        val visibleTop = -transform.y / transform.scale
        val visibleBottom = (height - transform.y) / transform.scale
        val visibleLeft = -transform.x / transform.scale
        val visibleRight = (width - transform.x) / transform.scale
        val zoomBucket = ceil(transform.scale * 2f) / 2f
        val renderWidth = ceil(width * zoomBucket).toInt()
        val tileRatio = renderWidth.toFloat() / width
        val needed = mutableListOf<Tile>()
        content.pages.forEachIndexed { index, page ->
            val top = pdfTops[index]
            val pageHeight = width.toFloat() * page.height / page.width
            if (top > visibleBottom || top + pageHeight < visibleTop) return@forEachIndexed
            canvas.drawRect(0f, top, width.toFloat(), top + pageHeight, paper)
            val startX = max(0, floor(visibleLeft * tileRatio / TILE_SIZE).toInt())
            val endX = min((renderWidth - 1) / TILE_SIZE, floor(visibleRight * tileRatio / TILE_SIZE).toInt())
            val renderHeight = ceil(pageHeight * tileRatio).toInt()
            val startY = max(0, floor((visibleTop - top) * tileRatio / TILE_SIZE).toInt())
            val endY = min((renderHeight - 1) / TILE_SIZE, floor((visibleBottom - top) * tileRatio / TILE_SIZE).toInt())
            for (y in startY..endY) for (x in startX..endX) {
                val tile = Tile(index, renderWidth, x, y)
                needed.add(tile)
                val bitmap = cache.get(tile)
                if (bitmap != null) {
                    tileBounds.set(x * TILE_SIZE / tileRatio, top + y * TILE_SIZE / tileRatio,
                        (x * TILE_SIZE + bitmap.width) / tileRatio, top + (y * TILE_SIZE + bitmap.height) / tileRatio)
                    canvas.drawBitmap(bitmap, null, tileBounds, paint)
                }
            }
        }
        if (needed != requestedTiles) {
            requestedTiles = needed
            requestTiles(needed)
        }
    }

    private fun requestTiles(tiles: List<Tile>) {
        if (disposed) return
        pendingTiles.set(tiles)
        if (!rendering.compareAndSet(false, true)) return
        executor.execute {
            try {
                while (!disposed) {
                    val batch = pendingTiles.getAndSet(emptyList())
                    if (batch.isEmpty()) break
                    for (tile in batch) {
                        if (disposed || pendingTiles.get().isNotEmpty()) break
                        if (cache.get(tile) != null || tile in failedTiles) continue
                        try {
                            val pdf = renderer ?: break
                            val bitmap = pdf.openPage(tile.page).use { page ->
                                val factor = tile.renderWidth.toFloat() / page.width
                                val tileWidth = min(TILE_SIZE, tile.renderWidth - tile.x * TILE_SIZE)
                                val tileHeight = min(TILE_SIZE, ceil(page.height * factor).toInt() - tile.y * TILE_SIZE)
                                val target = Bitmap.createBitmap(tileWidth, tileHeight, Bitmap.Config.ARGB_8888)
                                target.eraseColor(Color.WHITE)
                                val matrix = Matrix().apply {
                                    setScale(factor, factor)
                                    postTranslate(-tile.x * TILE_SIZE.toFloat(), -tile.y * TILE_SIZE.toFloat())
                                }
                                page.render(target, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                target
                            }
                            if (!disposed) cache.put(tile, bitmap)
                            post { if (!disposed) invalidate() }
                        } catch (_: Exception) {
                            failedTiles.add(tile)
                            post { if (!disposed) onStatus(false, "PDFのページを描画できません。ファイルを確認してください。") }
                        }
                    }
                }
            } finally {
                rendering.set(false)
                post { if (!disposed && pendingTiles.get().isNotEmpty()) requestTiles(pendingTiles.get()) }
            }
        }
    }

    private fun checkActive(ticket: Int) {
        if (disposed || revision != ticket) throw IOException("読み込みを中止しました。")
    }

    private fun message(error: Exception): String = when (error) {
        is SecurityException -> "アクセスできません。設定でフォルダを追加し直してください。"
        is CharacterCodingException -> "この文字コードでは読み込めません。文字コードを切り替えてください。"
        is IllegalArgumentException -> if (file.kind == DocumentKind.TEXT) error.message ?: "txtを読み込めません。" else "ファイルが破損しているか、対応していない形式です。"
        is IOException -> if (error.message?.endsWith("。") == true) error.message!! else "ファイルを読み込めません。保存先とファイルを確認してください。"
        else -> "ファイルを読み込めません。保存先とファイルを確認してください。"
    }

    private fun closePdf() {
        try { renderer?.close() } finally {
            renderer = null
            temporaryFile?.delete()
            temporaryFile = null
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        imageLoadJob.cancel()
        scene = null
        pendingTiles.set(emptyList())
        executor.execute { try { closePdf() } finally { cache.evictAll() } }
        executor.shutdown()
    }

    companion object {
        private const val TILE_SIZE = 512
        private const val PAGE_GAP = 12f
    }
}
