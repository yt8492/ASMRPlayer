package com.yt8492.asmrplayer.ui.preview

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
import android.util.LruCache
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** PdfRenderer・PFD・一時ファイルは共有された直列executorでのみ扱う。 */
internal class PdfPreviewRenderer(
    private val context: Context,
    private val executor: java.util.concurrent.ExecutorService,
    private val postUi: (() -> Unit) -> Unit,
    private val onInvalidate: () -> Unit,
    private val onError: (String) -> Unit,
) {
    @Volatile private var disposed = false
    private var renderer: PdfRenderer? = null
    private var temporaryFile: File? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tileBounds = RectF()
    private val paper = Paint().apply { color = Color.WHITE }
    private val pendingTiles = AtomicReference<List<Tile>>(emptyList())
    private val rendering = AtomicBoolean(false)
    private val failedTiles = mutableSetOf<Tile>()
    private val cache = object : LruCache<Tile, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Tile, value: Bitmap) = value.allocationByteCount
    }
    private var requestedTiles = emptyList<Tile>()
    private val pageLayouts = PdfPageLayoutCache()
    private data class Tile(val page: Int, val renderWidth: Int, val x: Int, val y: Int)

    fun load(uri: Uri, checkActive: () -> Unit): List<PageSize> {
        close()
        val pdf = openPdf(uri, checkActive)
        renderer = pdf
        if (pdf.pageCount == 0) throw IOException("このPDFにはページがありません。")
        return (0 until pdf.pageCount).map { index ->
            checkActive()
            pdf.openPage(index).use { PageSize(it.width, it.height) }
        }
    }

    fun resetGeometry() { requestedTiles = emptyList() }

    fun contentHeight(pages: List<PageSize>, width: Int): Float = pageLayouts.get(pages, width).contentHeight

    private fun openPdf(uri: Uri, checkActive: () -> Unit): PdfRenderer {
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
                    checkActive()
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

    fun draw(canvas: Canvas, pages: List<PageSize>, width: Int, height: Int, transform: PreviewTransform) {
        val layout = pageLayouts.get(pages, width)
        val visibleTop = -transform.y / transform.scale
        val visibleBottom = (height - transform.y) / transform.scale
        val visibleLeft = -transform.x / transform.scale
        val visibleRight = (width - transform.x) / transform.scale
        val zoomBucket = ceil(transform.scale * 2f) / 2f
        val renderWidth = ceil(width * zoomBucket).toInt()
        val tileRatio = renderWidth.toFloat() / width
        val needed = mutableListOf<Tile>()
        for (index in layout.visiblePages(visibleTop, visibleBottom)) {
            val top = layout.top(index)
            val pageHeight = layout.height(index)
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
                            postUi { if (!disposed) onInvalidate() }
                        } catch (_: Exception) {
                            failedTiles.add(tile)
                            postUi { if (!disposed) onError( "PDFのページを描画できません。ファイルを確認してください。") }
                        }
                    }
                }
            } finally {
                rendering.set(false)
                postUi { if (!disposed && pendingTiles.get().isNotEmpty()) requestTiles(pendingTiles.get()) }
            }
        }
    }

    fun close() {
        try { renderer?.close() } finally {
            renderer = null
            temporaryFile?.delete()
            temporaryFile = null
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        pendingTiles.set(emptyList())
        executor.execute { try { close() } finally { cache.evictAll() } }
    }

    companion object {
        private const val TILE_SIZE = 512
    }
}
