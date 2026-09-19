package com.yt8492.asmrplayer.data.repository

import android.net.Uri

internal data class DocumentPath(val treeUri: String, val documentId: String) {
    fun encode(): String = "saf://${Uri.encode(treeUri)}/${Uri.encode(documentId)}/"

    companion object {
        fun isDocumentPath(path: String): Boolean = path.startsWith("saf://")

        fun parse(path: String): DocumentPath? {
            if (!isDocumentPath(path)) return null
            val parts = path.removePrefix("saf://").trimEnd('/').split('/')
            if (parts.size != 2) return null
            return DocumentPath(Uri.decode(parts[0]), Uri.decode(parts[1]))
        }
    }
}

// MediaStoreのIDを維持し、追加フォルダには独立した正のID領域を割り当てる。
internal const val DOCUMENT_TRACK_ID_BASE = 1L shl 62
internal fun documentTrackId(id: Long): Long {
    require(id in 1 until DOCUMENT_TRACK_ID_BASE)
    return DOCUMENT_TRACK_ID_BASE + id
}
