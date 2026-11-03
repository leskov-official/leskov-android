package com.example.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class LeskovMediaService : Service() {

    private val binder = LocalBinder()
    private var powerManager: PowerManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isPlaying = false
    private var currentTitle: String = "LESKOV"
    private var currentArtist: String = "Igor Leskov"

    private var isReceiverRegistered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent?.action) {
                // Physical headset unplugged or Bluetooth disconnected -> pause web player
                Log.d(TAG, "Audio becoming noisy - pausing playback")
                pausePlayback()
                notifyWebClientsPause()
            }
        }
    }

    // Note: Chromium WebView internally manages system audio focus for HTML5 <audio> elements.
    // Having LeskovMediaService also request AUDIOFOCUS_GAIN caused an audio focus conflict within
    // the same process, where Android's AudioManager dispatched AUDIOFOCUS_LOSS (-1) and prematurely
    // paused playback. LeskovMediaService acts as the media controller and foreground lifecycle keeper.

    inner class LocalBinder : Binder() {
        fun getService(): LeskovMediaService = this@LeskovMediaService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager

        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LESKOV:AudioPlaybackWakeLock"
        )?.apply {
            setReferenceCounted(false)
        }

        createNotificationChannel()

        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(noisyReceiver, filter)
        isReceiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        when (action) {
            ACTION_PLAY -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: currentTitle
                val artist = intent.getStringExtra(EXTRA_ARTIST) ?: currentArtist
                startPlayback(title, artist)
            }
            ACTION_PAUSE -> {
                // Simply update the service playback state and notification.
                // CRITICAL: DO NOT call notifyWebClientsPause() here, because this intent
                // was sent FROM the web player reporting its pause state! Calling notifyWebClientsPause()
                // here caused an infinite echo loop that immediately paused newly started tracks.
                pausePlayback()
            }
            ACTION_USER_TOGGLE_PLAY_PAUSE -> {
                // Triggered by the user interacting with the native Android notification
                if (isPlaying) {
                    pausePlayback()
                    notifyWebClientsPause()
                } else {
                    notifyWebClientsPlay()
                }
            }
            ACTION_USER_STOP -> {
                // Triggered by the user clicking Close in the native Android notification
                stopPlayback()
                notifyWebClientsPause()
                stopSelf()
            }
            ACTION_HANDOFF_SILENT -> {
                // Media3 has assumed ownership. Don't send a pause back to the new page.
                stopPlayback()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    fun startPlayback(title: String, artist: String) {
        currentTitle = if (title.isBlank()) "LESKOV" else title
        currentArtist = if (artist.isBlank()) "Igor Leskov" else artist
        isPlaying = true

        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(3 * 60 * 60 * 1000L) // 3 hours timeout safety
            }
        } catch (_: Exception) {}

        val notification = buildNotification(isPlaying, currentTitle, currentArtist)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun pausePlayback() {
        isPlaying = false
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}

        val notification = buildNotification(isPlaying, currentTitle, currentArtist)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        try { nm?.notify(NOTIFICATION_ID, notification) }
        catch (e: SecurityException) { Log.w(TAG, "Notification permission not granted", e) }

        // Detach from foreground state while paused so Android doesn't treat it as active ongoing foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
    }

    fun stopPlayback() {
        isPlaying = false
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LESKOV Музыка",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Управление воспроизведением музыки в фоновом режиме"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(playing: Boolean, title: String, artist: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, LeskovMediaService::class.java).apply {
            action = ACTION_USER_TOGGLE_PLAY_PAUSE
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            1,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LeskovMediaService::class.java).apply {
            action = ACTION_USER_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (playing) "Пауза" else "Воспроизведение"

        val largeIconBitmap = try {
            BitmapFactory.decodeResource(resources, R.drawable.leskov_logo)
        } catch (_: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText("LESKOV")
            // CRITICAL: Monochrome silhouette vector icon prevents white square artifact on Android 12+
            .setSmallIcon(R.drawable.ic_notification_leskov)
            .setContentIntent(openPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(playing)
            .setColor(0xFF9B8CFB.toInt())
            .addAction(playPauseIcon, playPauseTitle, togglePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Закрыть", stopPendingIntent)

        if (largeIconBitmap != null) {
            builder.setLargeIcon(largeIconBitmap)
        }

        return builder.build()
    }

    override fun onDestroy() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(noisyReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        stopPlayback()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LeskovMediaService"

        const val CHANNEL_ID = "leskov_playback_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_PLAY = "com.example.media.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.media.ACTION_PAUSE"
        const val ACTION_USER_TOGGLE_PLAY_PAUSE = "com.example.media.ACTION_USER_TOGGLE_PLAY_PAUSE"
        const val ACTION_USER_STOP = "com.example.media.ACTION_USER_STOP"
        private const val ACTION_HANDOFF_SILENT = "com.example.media.ACTION_HANDOFF_SILENT"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"

        // State deduplication to prevent spurious intent churning
        private var lastReportedState: Boolean? = null
        private var lastReportedTitle: String? = null
        private var lastReportedArtist: String? = null

        // Callback listeners for webview action binding
        var onMediaActionRequested: ((action: String) -> Unit)? = null

        fun notifyWebClientsPause() {
            onMediaActionRequested?.invoke("pause")
        }

        fun notifyWebClientsPlay() {
            onMediaActionRequested?.invoke("play")
        }

        fun updatePlayback(context: Context, isPlaying: Boolean, title: String?, artist: String?) {
            if (LeskovNativeAudioService.isActive()) return
            // Idempotent state deduplication: do not churn if state didn't change
            if (lastReportedState == isPlaying && lastReportedTitle == title && lastReportedArtist == artist) {
                return
            }
            val previousState = lastReportedState
            lastReportedState = isPlaying
            lastReportedTitle = title
            lastReportedArtist = artist

            // Do not launch a background Service for an idle page or a redundant pause.
            // A pause is only meaningful if an already-running media notification existed.
            if (!isPlaying && previousState != true) return

            try {
                val intent = Intent(context, LeskovMediaService::class.java).apply {
                    action = if (isPlaying) ACTION_PLAY else ACTION_PAUSE
                    putExtra(EXTRA_TITLE, title ?: "LESKOV")
                    putExtra(EXTRA_ARTIST, artist ?: "Igor Leskov")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPlaying) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send updatePlayback intent", e)
            }
        }

        fun stopSilently(context: Context) {
            lastReportedState = null
            lastReportedTitle = null
            lastReportedArtist = null
            try {
                context.startService(Intent(context, LeskovMediaService::class.java).apply {
                    action = ACTION_HANDOFF_SILENT
                })
            } catch (_: Exception) { }
        }

        fun stop(context: Context) {
            lastReportedState = null
            lastReportedTitle = null
            lastReportedArtist = null
            try {
                val intent = Intent(context, LeskovMediaService::class.java).apply {
                    action = ACTION_USER_STOP
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }
}
