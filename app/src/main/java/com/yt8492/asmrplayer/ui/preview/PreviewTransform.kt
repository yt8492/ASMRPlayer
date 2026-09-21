package com.yt8492.asmrplayer.ui.preview

/** 表示座標で移動量を保持し、画像と縦長文書で同じ操作を使う。 */
internal class PreviewTransform {
    var scale = 1f
        private set
    var x = 0f
        private set
    var y = 0f
        private set
    var viewportWidth = 0f
    var viewportHeight = 0f
    var contentWidth = 0f
    var contentHeight = 0f

    fun zoom(factor: Float, focusX: Float, focusY: Float) {
        val next = (scale * factor).coerceIn(1f, 5f)
        val ratio = next / scale
        x = focusX - (focusX - x) * ratio
        y = focusY - (focusY - y) * ratio
        scale = next
        clamp()
    }

    fun pan(dx: Float, dy: Float) {
        x += dx
        y += dy
        clamp()
    }

    fun reset() {
        // 文書の読み進めた位置は保ち、倍率と横位置を戻す。
        y /= scale
        scale = 1f
        x = 0f
        clamp()
    }

    fun clamp() {
        fun bound(offset: Float, viewport: Float, content: Float): Float =
            if (content <= viewport) (viewport - content) / 2f else offset.coerceIn(viewport - content, 0f)
        x = bound(x, viewportWidth, contentWidth * scale)
        y = bound(y, viewportHeight, contentHeight * scale)
    }
}
