package com.yt8492.asmrplayer.ui.preview

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yt8492.asmrplayer.data.model.DocumentKind
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewContentLoaderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val executor = Executors.newSingleThreadExecutor()
    private val pdf = PdfPreviewRenderer(context, executor, { it() }, {}, {})
    private val job = Job()
    private val loader = PreviewContentLoader(context, job, pdf)
    private val files = mutableListOf<File>()

    @After fun close() {
        loader.clear()
        job.cancel()
        pdf.dispose()
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        files.forEach { it.delete() }
    }

    private fun fixture(text: String): File = File.createTempFile("text-layout-test", ".txt", context.cacheDir)
        .also { it.writeText(text); files.add(it) }

    private fun preview(file: File) = PreviewFile(Uri.fromFile(file).toString(), file.name, DocumentKind.TEXT)

    @Test fun 幅変更ではファイルを開き直さず同じ本文を組版する() {
        val text = "日本語のプレビューと折り返しの確認。".repeat(30)
        val file = fixture(text)
        val preview = preview(file)
        val wide = loader.load(preview, TextEncoding.AUTO, 600, {}, { _, _ -> }) as PreviewScene.Text
        assertTrue(file.delete())
        var size = 0L
        var encoding: TextEncoding? = null
        val narrow = loader.load(preview, TextEncoding.AUTO, 180, {}, { bytes, detected -> size = bytes; encoding = detected }) as PreviewScene.Text
        assertEquals(text, narrow.layout.text.toString())
        assertTrue(narrow.layout.lineCount > wide.layout.lineCount)
        assertEquals(text.toByteArray().size.toLong(), size)
        assertEquals(TextEncoding.UTF8, encoding)
        // キャッシュがあっても読み込み中止の要求は尊重する。
        assertTrue(runCatching {
            loader.load(preview, TextEncoding.AUTO, 180, { throw IOException("中止") }, { _, _ -> })
        }.isFailure)
    }

    @Test fun 別の文書や文字コードに切り替えると古い本文を使わない() {
        val first = fixture("最初の文書")
        val second = fixture("別の文書")
        loader.load(preview(first), TextEncoding.AUTO, 400, {}, { _, _ -> })
        val different = loader.load(preview(second), TextEncoding.AUTO, 400, {}, { _, _ -> }) as PreviewScene.Text
        assertEquals("別の文書", different.layout.text.toString())
        second.writeBytes("文字コードを変更".toByteArray(Charsets.UTF_16LE))
        val changed = loader.load(preview(second), TextEncoding.UTF16_LE, 400, {}, { _, _ -> }) as PreviewScene.Text
        assertEquals("文字コードを変更", changed.layout.text.toString())
        loader.clear()
        second.writeText("再読み込み")
        val reread = loader.load(preview(second), TextEncoding.AUTO, 400, {}, { _, _ -> }) as PreviewScene.Text
        assertEquals("再読み込み", reread.layout.text.toString())
    }

    @Test fun 読み込みに失敗した文書をキャッシュに残さない() {
        val file = fixture("")
        assertTrue(runCatching { loader.load(preview(file), TextEncoding.AUTO, 400, {}, { _, _ -> }) }.isFailure)
        file.writeText("修復後の本文")
        val scene = loader.load(preview(file), TextEncoding.AUTO, 400, {}, { _, _ -> }) as PreviewScene.Text
        assertEquals("修復後の本文", scene.layout.text.toString())
    }
}
