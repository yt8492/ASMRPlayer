package com.yt8492.asmrplayer.data.model

import android.net.Uri

enum class DocumentKind { PDF, TEXT }

data class DocumentFile(
    val id: Long,
    val name: String,
    val uri: Uri,
    val kind: DocumentKind,
    val size: Long?,
)

internal fun documentKind(mimeType: String, name: String): DocumentKind? = when (mimeType.lowercase()) {
    "application/pdf" -> DocumentKind.PDF
    "text/plain" -> DocumentKind.TEXT
    "", "application/octet-stream" -> when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> DocumentKind.PDF
        "txt" -> DocumentKind.TEXT
        else -> null
    }
    else -> null
}
