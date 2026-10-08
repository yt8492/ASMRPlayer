package com.yt8492.asmrplayer.data.repository.impl

import android.net.Uri
import com.yt8492.asmrplayer.data.datasource.document.ArtworkPermissionSource
import com.yt8492.asmrplayer.data.model.QueueArtworkTarget
import com.yt8492.asmrplayer.data.repository.ArtworkRepository
import com.yt8492.asmrplayer.data.repository.QueueArtworkRepository
import com.yt8492.asmrplayer.data.repository.TrackArtworkRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.yt8492.asmrplayer.core.coroutines.runSuspendCatching
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

internal class ArtworkRepositoryImpl(
    private val tracks: TrackArtworkRepository,
    private val queues: QueueArtworkRepository,
    private val permissions: ArtworkPermissionSource,
) : ArtworkRepository {
    private val mutex = Mutex()
    override fun observeTrackArtwork(trackId: Long) = tracks.observeTrackArtwork(trackId).map { it?.imageUri }
    override fun observeQueueArtwork(target: QueueArtworkTarget) = queues.observeQueueArtwork(target.type, target.key).map { it?.imageUri }
    override fun observeResolvedArtwork(trackId: Long, target: QueueArtworkTarget, fallback: Uri?) =
        combine(observeTrackArtwork(trackId), observeQueueArtwork(target)) { track, queue -> track ?: queue ?: fallback }

    override suspend fun saveTrackArtwork(trackId: Long, uri: Uri) = mutex.withLock {
        val previous = tracks.getTrackArtwork(trackId)?.imageUri
        saveWithPermission(uri) { tracks.saveTrackArtwork(trackId, uri) }
        if (previous != null && previous != uri) releaseIfUnused(previous)
    }
    override suspend fun deleteTrackArtwork(trackId: Long) = mutex.withLock {
        val previous = tracks.getTrackArtwork(trackId)?.imageUri
        tracks.deleteTrackArtwork(trackId)
        previous?.let { releaseIfUnused(it) }
        Unit
    }
    override suspend fun saveQueueArtwork(target: QueueArtworkTarget, uri: Uri) = mutex.withLock {
        val previous = queues.getQueueArtwork(target.type, target.key)?.imageUri
        saveWithPermission(uri) { queues.saveQueueArtwork(target.type, target.key, uri) }
        if (previous != null && previous != uri) releaseIfUnused(previous)
    }
    override suspend fun deleteQueueArtwork(target: QueueArtworkTarget) = mutex.withLock {
        val previous = queues.getQueueArtwork(target.type, target.key)?.imageUri
        queues.deleteQueueArtwork(target.type, target.key)
        previous?.let { releaseIfUnused(it) }
        Unit
    }
    private suspend fun saveWithPermission(uri: Uri, save: suspend () -> Unit) {
        permissions.acquire(uri)
        try {
            save()
        } catch (error: Exception) {
            // DB書込失敗・キャンセル時にも、参照されなかった権限を残さない。
            withContext(NonCancellable) {
                runSuspendCatching { releaseIfUnused(uri) }.onFailure { cleanupError ->
                    Timber.w(cleanupError, "保存失敗後の画像 URI 権限解放に失敗しました")
                }
            }
            throw error
        }
    }

    private suspend fun releaseIfUnused(uri: Uri) {
        if (tracks.isImageUriUsed(uri) || queues.isImageUriUsed(uri)) return
        try { permissions.release(uri) } catch (error: SecurityException) {
            Timber.w(error, "未使用画像 URI 権限の解放に失敗しました uriScheme=%s", uri.scheme)
        }
    }
}
