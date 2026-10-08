package com.yt8492.asmrplayer.data.datasource.document

/** 保存方式に依存しない、DocumentsProviderから読み取った文書情報。 */
internal data class FolderDocument(
    val treeUri: String,
    val documentId: String,
    val parentId: String?,
    val name: String,
    val mimeType: String,
    val size: Long?,
    val modifiedAt: Long,
    val title: String = name,
    val artist: String = "",
    val durationMs: Long = 0,
    val trackNumber: Int = 0,
)
