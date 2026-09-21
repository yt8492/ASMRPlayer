package com.yt8492.asmrplayer.ui.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ApplicationProvider
import com.yt8492.asmrplayer.data.model.DocumentKind
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PreviewCanvasTest {
    @get:Rule val rule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = mutableListOf<File>()
    private lateinit var view: PreviewCanvasView
    @Volatile private var loaded = false
    @Volatile private var error: String? = null

    @After fun cleanUp() {
        if (::view.isInitialized) rule.runOnIdle { view.dispose() }
        files.forEach { it.delete() }
    }

    private fun fixture(suffix: String) = File.createTempFile("preview-test", suffix, context.cacheDir).also { files.add(it) }

    private fun show(file: File, kind: DocumentKind?, encoding: TextEncoding = TextEncoding.AUTO) =
        showUri(Uri.fromFile(file), kind, encoding)

    private fun showUri(uri: Uri, kind: DocumentKind?, encoding: TextEncoding = TextEncoding.AUTO) {
        rule.setContent {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = {
                PreviewCanvasView(it, PreviewFile(uri.toString(), "検証用ファイル", kind), encoding,
                    onStatus = { busy, message -> error = message; loaded = !busy }, onScale = {},
                ).also { canvas -> view = canvas }
            }, onRelease = { it.dispose() })
        }
        rule.waitUntil(20_000) { loaded }
    }

    @Test fun txtを実際のピンチとドラッグで拡大して最後まで読める() {
        val file = fixture(".txt").apply { writeText((1..200).joinToString("\n") { "説明の行 $it 日本語のプレビュー" }) }
        show(file, DocumentKind.TEXT)
        assertNull(error)
        rule.runOnIdle {
            pinch()
            assertTrue("実際の倍率=${view.transform.scale}", view.transform.scale > 1.5f)
            assertTrue(view.transform.scale <= 5f)
            repeat(40) { swipeUp() }
            assertEquals(view.height - view.transform.contentHeight * view.transform.scale, view.transform.y, 1f)
            view.resetZoom()
            assertEquals(1f, view.transform.scale, 0f)
        }
    }

    @Test fun 複数ページPDFの描画と拡大後の最終ページを確認する() {
        val file = fixture(".pdf")
        val pdf = PdfDocument()
        try {
            listOf(Color.RED, Color.BLUE).forEachIndexed { index, color ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(600, 1600, index).create())
                page.canvas.drawColor(color)
                pdf.finishPage(page)
            }
            file.outputStream().use { pdf.writeTo(it) }
        } finally { pdf.close() }
        show(file, DocumentKind.PDF)
        assertNull(error)
        waitForCenterColor(Color.RED)
        rule.runOnIdle {
            pinch()
            assertTrue("実際の倍率=${view.transform.scale}", view.transform.scale > 1.5f)
            repeat(40) { swipeUp() }
        }
        waitForCenterColor(Color.BLUE)
        rule.runOnIdle {
            view.resetZoom()
            assertEquals(1f, view.transform.scale, 0f)
        }
    }

    @Test fun 画像を描画しダブルタップで拡大と復帰ができる() {
        val file = fixture(".png")
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GREEN)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        show(file, null)
        assertNull(error)
        waitForCenterColor(Color.GREEN)
        rule.runOnIdle {
            doubleTap()
            assertEquals(2f, view.transform.scale, 0f)
        }
        // GestureDetectorの連続タップ判定期間を越えてから次の操作を行う。
        SystemClock.sleep(350)
        rule.runOnIdle {
            doubleTap()
            assertEquals(1f, view.transform.scale, 0f)
        }
    }

    @Test fun 破損PDFはエラーになり閉じられる() {
        show(fixture(".pdf").apply { writeText("not a PDF") }, DocumentKind.PDF)
        assertNotNull(error)
        rule.runOnIdle { view.dispose(); view.dispose() }
    }

    @Test fun 空のtxtは空ファイルと表示する() {
        show(fixture(".txt"), DocumentKind.TEXT)
        assertEquals("このtxtファイルは空です。", error)
    }

    @Test fun 上限を超えるtxtはエラーになる() {
        show(fixture(".txt").apply { writeBytes(ByteArray(MAX_TEXT_BYTES + 1)) }, DocumentKind.TEXT)
        assertEquals("txtは5 MiBまでプレビューできます。", error)
    }

    @Test fun ShiftJISの日本語txtを実機で表示できる() {
        show(fixture(".txt").apply { writeBytes("日本語の説明".toByteArray(charset("windows-31j"))) }, DocumentKind.TEXT)
        assertNull(error)
    }

    @Test fun 開けないファイルでも終了できる() {
        val file = fixture(".txt").apply { delete() }
        show(file, DocumentKind.TEXT)
        assertNotNull(error)
    }

    @Test fun シークできないPDFを表示して一時ファイルを削除する() {
        fun temporaryFiles() = context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("preview-") && it.extension == "pdf" }.toSet()
        val before = temporaryFiles()
        showUri(Uri.parse("content://com.yt8492.asmrplayer.previewtest/pipe"), DocumentKind.PDF)
        assertNull(error)
        waitForCenterColor(Color.MAGENTA)
        assertTrue(temporaryFiles().size > before.size)
        rule.runOnIdle { view.dispose() }
        rule.waitUntil(10_000) { temporaryFiles() == before }
    }

    @Test fun アクセス権喪失時にはフォルダ再追加を案内する() {
        showUri(Uri.parse("content://com.yt8492.asmrplayer.previewtest/denied"), DocumentKind.TEXT)
        assertEquals("アクセスできません。設定でフォルダを追加し直してください。", error)
    }

    private fun waitForCenterColor(expected: Int) {
        rule.waitUntil(15_000) {
            var actual = 0
            rule.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                actual = bitmap.getPixel(view.width / 2, view.height / 2)
                bitmap.recycle()
            }
            actual == expected
        }
    }

    private fun event(action: Int, x: Float, y: Float, down: Long, time: Long) {
        MotionEvent.obtain(down, time, action, x, y, 0).also { view.dispatchTouchEvent(it); it.recycle() }
    }

    private fun swipeUp() {
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, view.width / 2f, view.height * .85f, down, down)
        for (i in 1..10) event(MotionEvent.ACTION_MOVE, view.width / 2f, view.height * (.85f - .07f * i), down, down + 20 * i)
        event(MotionEvent.ACTION_UP, view.width / 2f, view.height * .15f, down, down + 220)
    }

    private fun doubleTap() {
        val down = SystemClock.uptimeMillis()
        val x = view.width / 2f; val y = view.height / 2f
        event(MotionEvent.ACTION_DOWN, x, y, down, down)
        event(MotionEvent.ACTION_UP, x, y, down, down + 30)
        event(MotionEvent.ACTION_DOWN, x, y, down + 100, down + 100)
        event(MotionEvent.ACTION_UP, x, y, down + 100, down + 130)
    }

    private fun pinch() {
        val down = SystemClock.uptimeMillis()
        val centerX = view.width / 2f; val centerY = view.height / 2f
        val span = view.width / 2.5f
        event(MotionEvent.ACTION_DOWN, centerX - span / 2, centerY, down, down)
        fun multi(action: Int, gap: Float, time: Long) {
            val properties = Array(2) { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            val coords = Array(2) { index -> MotionEvent.PointerCoords().apply {
                x = centerX + (if (index == 0) -gap else gap) / 2f; y = centerY; pressure = 1f; size = 1f
            } }
            MotionEvent.obtain(down, time, action, 2, properties, coords, 0, 0, 1f, 1f, 0, 0,
                android.view.InputDevice.SOURCE_TOUCHSCREEN, 0).also { view.dispatchTouchEvent(it); it.recycle() }
        }
        multi(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), span, down + 10)
        for (i in 1..10) multi(MotionEvent.ACTION_MOVE, span * (1 + i / 10f), down + 20 * i)
        multi(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), span * 2, down + 220)
        event(MotionEvent.ACTION_UP, centerX - span, centerY, down, down + 230)
    }
}
