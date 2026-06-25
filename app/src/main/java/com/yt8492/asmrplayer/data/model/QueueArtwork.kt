package com.yt8492.asmrplayer.data.model

import android.net.Uri

data class QueueArtwork(
    val queueType: String,
    val queueKey: String,
    val imageUri: Uri,
)
