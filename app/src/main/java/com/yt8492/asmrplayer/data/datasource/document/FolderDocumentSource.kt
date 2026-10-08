package com.yt8492.asmrplayer.data.datasource.document

import android.net.Uri

internal interface FolderDocumentSource {
    fun hasPermission(uri: String): Boolean
    fun persistPermission(uri: Uri)
    fun releasePermission(uri: String)
    suspend fun queryDocuments(uri: Uri, treeUri: String, parentId: String?): List<FolderDocument>
    fun readAudioMetadata(item: FolderDocument): FolderDocument
}
