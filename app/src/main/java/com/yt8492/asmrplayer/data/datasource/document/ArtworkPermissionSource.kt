package com.yt8492.asmrplayer.data.datasource.document

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

internal interface ArtworkPermissionSource {
    fun acquire(uri: Uri)
    fun release(uri: Uri)
}

internal class AndroidArtworkPermissionSource(private val resolver: ContentResolver) : ArtworkPermissionSource {
    override fun acquire(uri: Uri) = resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    override fun release(uri: Uri) = resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
