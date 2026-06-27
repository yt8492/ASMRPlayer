package com.yt8492.asmrplayer.data.model

import android.net.Uri

data class ImageFile(
    val id: Long,
    val title: String,
    val uri: Uri,
    val mimeType: String,
)
