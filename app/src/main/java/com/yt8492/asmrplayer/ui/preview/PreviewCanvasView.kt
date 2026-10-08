package com.yt8492.asmrplayer.ui.preview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.yt8492.asmrplayer.data.model.DocumentKind
import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.util.concurrent.Executors
import kotlin.math.min
import kotlinx.coroutines.Job

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
    private var scene: PreviewScene? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val imageBounds = RectF()
    private val textPaper = Paint().apply { color = Color.WHITE }
    private var textColor = Color.BLACK
    private val pdf = PdfPreviewRenderer(context, executor,
        postUi = { action -> post { action() } }, onInvalidate = ::invalidate,
        onError = { error -> onStatus(false, error) })
    private val loader = PreviewContentLoader(context, imageLoadJob, pdf)

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
                val loaded = loader.load(file, encoding, viewWidth, { checkActive(ticket) }) { size, detected ->
                    post { if (!disposed && revision == ticket) onTextInfo(size, detected) }
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
                if (file.kind == DocumentKind.PDF) pdf.close()
                post { if (!disposed && revision == ticket) onStatus(false, message(error)) }
            }
        }
    }

    private fun updateGeometry() {
        transform.contentWidth = width.toFloat()
        transform.contentHeight = when (val content = scene) {
            is PreviewScene.Image -> {
                val ratio = min(width.toFloat() / content.bitmap.width, height.toFloat() / content.bitmap.height)
                transform.contentWidth = content.bitmap.width * ratio
                content.bitmap.height * ratio
            }
            is PreviewScene.Text -> content.layout.height + 2 * content.padding
            is PreviewScene.Pdf -> pdf.contentHeight(content.pages, width)
            null -> 0f
        }
        transform.clamp()
        pdf.resetGeometry()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (disposed) return
        canvas.save()
        canvas.translate(transform.x, transform.y)
        canvas.scale(transform.scale, transform.scale)
        when (val content = scene) {
            is PreviewScene.Image -> {
                imageBounds.set(0f, 0f, transform.contentWidth, transform.contentHeight)
                canvas.drawBitmap(content.bitmap, null, imageBounds, paint)
            }
            is PreviewScene.Text -> {
                canvas.drawRect(0f, 0f, transform.contentWidth, transform.contentHeight, textPaper)
                canvas.translate(content.padding, content.padding)
                content.layout.paint.color = textColor
                // StaticLayoutはCanvasのクリップ範囲内の行だけを描画する。
                content.layout.draw(canvas)
            }
            is PreviewScene.Pdf -> pdf.draw(canvas, content.pages, width, height, transform)
            null -> Unit
        }
        canvas.restore()
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

    fun dispose() {
        if (disposed) return
        disposed = true
        imageLoadJob.cancel()
        scene = null
        pdf.dispose()
        executor.execute { loader.clear() }
        executor.shutdown()
    }

}
