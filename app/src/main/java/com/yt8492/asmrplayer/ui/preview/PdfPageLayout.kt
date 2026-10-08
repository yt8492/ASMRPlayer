package com.yt8492.asmrplayer.ui.preview

/** 表示幅が変わるまでページ位置を保持し、表示範囲を二分探索する。 */
internal class PdfPageLayout(val pages: List<PageSize>, val width: Int) {
    private val tops = FloatArray(pages.size)
    private val bottoms = FloatArray(pages.size)
    private val heights = FloatArray(pages.size)
    val contentHeight: Float

    init {
        var top = 0f
        pages.forEachIndexed { index, page ->
            tops[index] = top
            heights[index] = width.toFloat() * page.height / page.width
            bottoms[index] = top + heights[index]
            top += heights[index] + PAGE_GAP
        }
        contentHeight = bottoms.lastOrNull() ?: 0f
    }

    fun top(index: Int): Float = tops[index]
    fun height(index: Int): Float = heights[index]

    fun visiblePages(visibleTop: Float, visibleBottom: Float): IntRange {
        if (visibleBottom < visibleTop) return IntRange.EMPTY
        val first = lowerBound(bottoms, visibleTop, skipEqual = false)
        val end = lowerBound(tops, visibleBottom, skipEqual = true)
        return first until end
    }

    private fun lowerBound(values: FloatArray, target: Float, skipEqual: Boolean): Int {
        var low = 0
        var high = values.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (values[middle] < target || (skipEqual && values[middle] == target)) low = middle + 1
            else high = middle
        }
        return low
    }

    companion object { const val PAGE_GAP = 12f }
}

internal class PdfPageLayoutCache {
    private var layout: PdfPageLayout? = null

    fun get(pages: List<PageSize>, width: Int): PdfPageLayout {
        val current = layout
        if (current != null && current.pages === pages && current.width == width) return current
        return PdfPageLayout(pages, width).also { layout = it }
    }
}
