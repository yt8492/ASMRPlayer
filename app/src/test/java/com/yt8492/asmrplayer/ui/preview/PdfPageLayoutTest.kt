package com.yt8492.asmrplayer.ui.preview

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class PdfPageLayoutTest {
    @Test fun ページ境界と余白で表示対象を正しく選ぶ() {
        val layout = PdfPageLayout(listOf(PageSize(100, 200), PageSize(100, 100)), 100)
        assertEquals(312f, layout.contentHeight, 0f)
        assertEquals(listOf(0), layout.visiblePages(0f, 200f).toList())
        assertTrue(layout.visiblePages(201f, 211f).isEmpty())
        assertEquals(listOf(0, 1), layout.visiblePages(200f, 212f).toList())
        assertEquals(listOf(1), layout.visiblePages(312f, 400f).toList())
        assertTrue(layout.visiblePages(313f, 400f).isEmpty())
        assertTrue(layout.visiblePages(-20f, -1f).isEmpty())
    }

    @Test fun サイズの違う多数のページでも全件走査と同じ表示範囲になる() {
        val random = Random(42)
        val pages = List(1000) { PageSize(random.nextInt(100, 800), random.nextInt(100, 1600)) }
        val layout = PdfPageLayout(pages, 480)
        repeat(500) {
            val top = random.nextFloat() * (layout.contentHeight + 2000) - 1000
            val bottom = top + random.nextFloat() * 1800
            val expected = pages.indices.filter { index ->
                layout.top(index) <= bottom && layout.top(index) + layout.height(index) >= top
            }
            assertEquals(expected, layout.visiblePages(top, bottom).toList())
        }
    }

    @Test fun 最終ページと空の文書を扱える() {
        val layout = PdfPageLayout(List(1000) { PageSize(100, 200) }, 100)
        assertEquals(listOf(999), layout.visiblePages(layout.contentHeight - 1, layout.contentHeight).toList())
        assertTrue(layout.visiblePages(10f, 0f).isEmpty())
        val empty = PdfPageLayout(emptyList(), 100)
        assertEquals(0f, empty.contentHeight, 0f)
        assertTrue(empty.visiblePages(0f, 100f).isEmpty())
    }

    @Test fun 同じ文書と幅のページ位置を再利用し変更時だけ再計算する() {
        val pages = listOf(PageSize(100, 200))
        val cache = PdfPageLayoutCache()
        val initial = cache.get(pages, 100)
        assertSame(initial, cache.get(pages, 100))
        val resized = cache.get(pages, 200)
        assertNotSame(initial, resized)
        assertEquals(400f, resized.contentHeight, 0f)
        val replaced = cache.get(listOf(PageSize(100, 300)), 200)
        assertNotSame(resized, replaced)
        assertEquals(600f, replaced.contentHeight, 0f)
    }
}
