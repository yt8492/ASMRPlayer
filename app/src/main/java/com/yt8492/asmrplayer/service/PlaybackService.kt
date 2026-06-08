package com.yt8492.asmrplayer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerNotificationManager
import com.yt8492.asmrplayer.MainActivity
import com.yt8492.asmrplayer.R

class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var notificationManager: PlayerNotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
        }
        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
        createNotificationChannel()
        notificationManager = PlayerNotificationManager.Builder(
            this,
            NOTIFICATION_ID,
            NOTIFICATION_CHANNEL_ID,
        )
            .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player): CharSequence {
                    return player.currentMediaItem?.mediaMetadata?.title ?: getString(R.string.app_name)
                }

                override fun createCurrentContentIntent(player: Player): PendingIntent {
                    val metadata = player.currentMediaItem?.mediaMetadata
                    val extras = metadata?.extras
                    val intent = Intent(this@PlaybackService, MainActivity::class.java).apply {
                        action = ACTION_OPEN_PLAYER
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(EXTRA_QUEUE_TYPE, extras?.getString(EXTRA_QUEUE_TYPE).orEmpty())
                        putExtra(EXTRA_ALBUM_ID, extras?.getLong(EXTRA_ALBUM_ID, -1L) ?: -1L)
                        putExtra(EXTRA_PLAYLIST_ID, extras?.getLong(EXTRA_PLAYLIST_ID, -1L) ?: -1L)
                        putExtra(EXTRA_TRACK_ID, player.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L)
                        putExtra(EXTRA_ALBUM_TITLE, metadata?.albumTitle?.toString().orEmpty())
                        putExtra(EXTRA_ALBUM_ART_URI, extras?.getString(EXTRA_ALBUM_ART_URI).orEmpty())
                        putExtra(EXTRA_PLAYLIST_NAME, extras?.getString(EXTRA_PLAYLIST_NAME).orEmpty())
                        putExtra(EXTRA_FOLDER_PATH, extras?.getString(EXTRA_FOLDER_PATH).orEmpty())
                        putExtra(EXTRA_FOLDER_TITLE, extras?.getString(EXTRA_FOLDER_TITLE).orEmpty())
                    }
                    return PendingIntent.getActivity(
                        this@PlaybackService,
                        0,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                }

                override fun getCurrentContentText(player: Player): CharSequence? {
                    val metadata = player.currentMediaItem?.mediaMetadata
                    return metadata?.artist ?: metadata?.albumTitle
                }

                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback,
                ) = null
            })
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: android.app.Notification,
                    ongoing: Boolean,
                ) {
                    if (ongoing) {
                        startForeground(notificationId, notification)
                    } else {
                        stopForeground(STOP_FOREGROUND_DETACH)
                    }
                }

                override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    player?.pause()
                    stopSelf()
                }
            })
            .setChannelNameResourceId(R.string.notification_channel_name)
            .setSmallIconResourceId(R.drawable.ic_launcher_foreground)
            .build().apply {
                setMediaSessionToken(mediaSession?.platformToken ?: return)
                setPlayer(exoPlayer)
            }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        notificationManager?.setPlayer(null)
        notificationManager = null
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "com.yt8492.asmrplayer.action.OPEN_PLAYER"
        const val EXTRA_QUEUE_TYPE = "com.yt8492.asmrplayer.extra.QUEUE_TYPE"
        const val EXTRA_ALBUM_ID = "com.yt8492.asmrplayer.extra.ALBUM_ID"
        const val EXTRA_PLAYLIST_ID = "com.yt8492.asmrplayer.extra.PLAYLIST_ID"
        const val EXTRA_TRACK_ID = "com.yt8492.asmrplayer.extra.TRACK_ID"
        const val EXTRA_ALBUM_TITLE = "com.yt8492.asmrplayer.extra.ALBUM_TITLE"
        const val EXTRA_ALBUM_ART_URI = "com.yt8492.asmrplayer.extra.ALBUM_ART_URI"
        const val EXTRA_PLAYLIST_NAME = "com.yt8492.asmrplayer.extra.PLAYLIST_NAME"
        const val EXTRA_FOLDER_PATH = "com.yt8492.asmrplayer.extra.FOLDER_PATH"
        const val EXTRA_FOLDER_TITLE = "com.yt8492.asmrplayer.extra.FOLDER_TITLE"
        const val QUEUE_TYPE_ALBUM = "album"
        const val QUEUE_TYPE_PLAYLIST = "playlist"
        const val QUEUE_TYPE_FOLDER = "folder"

        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "asmrplayer_playback"
    }
}
