package com.yt8492.asmrplayer.playback

import androidx.media3.common.Player
import com.yt8492.asmrplayer.data.model.Track
import com.yt8492.asmrplayer.playback.model.PlaybackQueue

/** 同じ要求の再接続では再生位置を維持し、別キューでは通知用メタデータも更新する。 */
internal fun Player.preparePlaybackQueue(queue: PlaybackQueue, tracks: List<Track>, startTrackId: Long, index: Int) {
    if (tracks.isEmpty()) return
    val startIndex = index.takeIf { it in tracks.indices } ?: 0
    val existing = List(mediaItemCount) { getMediaItemAt(it) }
    val sameQueue = existing.map { it.mediaId } == tracks.map { it.id.toString() } &&
        existing.all { it.mediaMetadata.extras?.toPlaybackQueue() == queue }
    if (sameQueue) {
        if (currentMediaItem?.mediaId != startTrackId.toString() || currentMediaItemIndex != startIndex) {
            seekTo(startIndex, 0)
            play()
        }
        return
    }
    setMediaItems(tracks.map { it.toMediaItem(queue) })
    seekTo(startIndex, 0)
    prepare()
    playWhenReady = true
}
