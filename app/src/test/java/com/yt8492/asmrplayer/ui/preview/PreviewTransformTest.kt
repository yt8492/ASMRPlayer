package com.yt8492.asmrplayer.ui.preview

import org.junit.Assert.*
import org.junit.Test

class PreviewTransformTest {
    private fun document() = PreviewTransform().apply {
        viewportWidth = 400f; viewportHeight = 600f
        contentWidth = 400f; contentHeight = 2400f
    }

    @Test fun ピンチ位置にある文書座標を保って拡大する() {
        val state = document()
        state.pan(0f, -300f)
        state.zoom(2f, 200f, 300f)
        assertEquals(200f, (200f - state.x) / state.scale, 0.01f)
        assertEquals(600f, (300f - state.y) / state.scale, 0.01f)
    }

    @Test fun 拡大中も最終行まで移動でき画面外には出ない() {
        val state = document()
        state.zoom(3f, 200f, 300f)
        state.pan(-10000f, -10000f)
        assertEquals(400f - 1200f, state.x, 0.01f)
        assertEquals(600f - 7200f, state.y, 0.01f)
        state.pan(10000f, 10000f)
        assertEquals(0f, state.x, 0.01f)
        assertEquals(0f, state.y, 0.01f)
    }

    @Test fun 倍率の上限下限とリセットを守る() {
        val state = document()
        state.zoom(99f, 0f, 0f)
        assertEquals(5f, state.scale, 0f)
        state.pan(0f, -1000f)
        state.reset()
        assertEquals(1f, state.scale, 0f)
        assertEquals(-200f, state.y, 0f)
        state.zoom(0.01f, 0f, 0f)
        assertEquals(1f, state.scale, 0f)
    }

    @Test fun 小さな画像は中央に収まり余白に移動できない() {
        val state = document().apply { contentWidth = 200f; contentHeight = 100f }
        state.clamp()
        state.pan(99f, -99f)
        assertEquals(100f, state.x, 0f)
        assertEquals(250f, state.y, 0f)
    }
}
