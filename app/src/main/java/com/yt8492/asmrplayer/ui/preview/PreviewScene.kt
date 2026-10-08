package com.yt8492.asmrplayer.ui.preview

import android.graphics.Bitmap
import android.text.StaticLayout

internal sealed interface PreviewScene {
    data class Image(val bitmap: Bitmap) : PreviewScene
    data class Text(val layout: StaticLayout, val padding: Float) : PreviewScene
    data class Pdf(val pages: List<PageSize>) : PreviewScene
}
internal data class PageSize(val width: Int, val height: Int)
