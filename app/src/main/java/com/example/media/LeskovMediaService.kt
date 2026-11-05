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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.net.Uri
import java.net.URL
import java.io.ByteArrayOutputStream
import javax.net.ssl.HttpsURLConnection
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.push.LeskovNotificationBrand
import com.example.MainActivity
import com.example.R

class LeskovMediaService : Service() {

    private val binder = LocalBinder()
    private var powerManager: PowerManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isPlaying = false
    private var currentTitle: String = "LESKOV"
    private var currentArtist: String = "Igor Leskov"
    private var artworkUrl: String = ""
    private var artworkBitmap: Bitmap? = null
    private var playbackPositionMs: Long = 0L
    private var playbackDurationMs: Long = 0L
    private var canPrevious: Boolean = false
    private var canNext: Boolean = false
    private var mediaSession: MediaSession? = null

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
        // Framework media session supplies track information, artwork and controls
        // to Android's notification shade and the device lock screen (API 26+).
        mediaSession = MediaSession(this, "LESKOV-WebAudio").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { notifyWebClientsPlay() }
                override fun onPause() { notifyWebClientsPause() }
                override fun onStop() { notifyWebClientsPause() }
                override fun onSkipToNext() { if (canNext) notifyWebClientsNext() }
                override fun onSkipToPrevious() { if (canPrevious) notifyWebClientsPrevious() }
                override fun onSeekTo(pos: Long) { if (playbackDurationMs > 0L) notifyWebClientsSeek(pos) }
            })
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }

        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        ContextCompat.registerReceiver(this, noisyReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        isReceiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        when (action) {
            ACTION_PLAY -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: currentTitle
                val artist = intent.getStringExtra(EXTRA_ARTIST) ?: currentArtist
                val cover = intent.getStringExtra(EXTRA_ARTWORK).orEmpty()
                playbackPositionMs = intent.getLongExtra(EXTRA_POSITION, 0L).coerceAtLeast(0L)
                playbackDurationMs = intent.getLongExtra(EXTRA_DURATION, 0L).coerceAtLeast(0L)
                canPrevious = intent.getBooleanExtra(EXTRA_CAN_PREVIOUS, false)
                canNext = intent.getBooleanExtra(EXTRA_CAN_NEXT, false)
                startPlayback(title, artist, cover)
            }
            ACTION_PROGRESS -> {
                playbackPositionMs = intent.getLongExtra(EXTRA_POSITION, 0L).coerceAtLeast(0L)
                playbackDurationMs = intent.getLongExtra(EXTRA_DURATION, 0L).coerceAtLeast(0L)
                canPrevious = intent.getBooleanExtra(EXTRA_CAN_PREVIOUS, false)
                canNext = intent.getBooleanExtra(EXTRA_CAN_NEXT, false)
                publishMediaSessionState()
            }
            ACTION_PAUSE -> {
                // Retain the last scrubber position even when browser playback pauses.
                playbackPositionMs = intent.getLongExtra(EXTRA_POSITION, playbackPositionMs).coerceAtLeast(0L)
                playbackDurationMs = intent.getLongExtra(EXTRA_DURATION, playbackDurationMs).coerceAtLeast(0L)
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
            ACTION_USER_PREVIOUS -> { if (canPrevious) notifyWebClientsPrevious() }
            ACTION_USER_NEXT -> { if (canNext) notifyWebClientsNext() }
            ACTION_USER_STOP -> {
                // Triggered by the user clicking Close in the native Android notification
                stopPlayback()
                notifyWebClientsPause()
                stopSelf()
            }

        }
        return START_NOT_STICKY
    }

    fun startPlayback(title: String, artist: String, cover: String) {
        currentTitle = if (title.isBlank()) "LESKOV" else title
        currentArtist = if (artist.isBlank()) "Igor Leskov" else artist
        isPlaying = true
        if (cover != artworkUrl) {
            artworkUrl = cover
            artworkBitmap = null
            loadArtworkAsync(cover)
        }

        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(3 * 60 * 60 * 1000L) // 3 hours timeout safety
            }
        } catch (_: Exception) {}

        mediaSession?.isActive = true
        publishMediaSessionState()
        val notification = buildNotification(isPlaying, currentTitle, currentArtist)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to promote web playback notification to foreground", e)
            stopSelf()
        }
    }

    fun pausePlayback() {
        isPlaying = false
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}

        publishMediaSessionState()
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
        publishMediaSessionState()
        mediaSession?.isActive = false
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

    // Only images from our own HTTPS host. Redirects, huge files and untrusted schemes are refused.
    private fun loadArtworkAsync(raw: String) {
        val artworkUri = try { Uri.parse(raw) } catch (_: Exception) { null } ?: return
        if (!artworkUri.scheme.equals("https", true) ||
            !artworkUri.host.equals("lesv.online", true) ||
            artworkUri.userInfo != null || artworkUri.port !in listOf(-1, 443)) return
        Thread {
            var connection: HttpsURLConnection? = null
            try {
                connection = URL(raw).openConnection() as HttpsURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.connect()
                if (connection.responseCode != 200 ||
                    !connection.contentType.orEmpty().startsWith("image/") ||
                    connection.contentLengthLong > 2_000_000L) return@Thread
                val bytes = connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    while (true) {
                        val count = input.read(chunk)
                        if (count < 0) break
                        if (output.size() + count > 2_000_000) return@Thread
                        output.write(chunk, 0, count)
                    }
                    output.toByteArray()
                }
                // A 2MB compressed JPEG can still decode into a huge bitmap and crash
                // the UI process or exceed Binder limits when sent as media artwork.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth < 1 || bounds.outHeight < 1) return@Thread
                var sample = 1
                while ((bounds.outWidth / sample) > 512 || (bounds.outHeight / sample) > 512) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@Thread
                Handler(Looper.getMainLooper()).post {
                    if (raw == artworkUrl && isPlaying) {
                        artworkBitmap = bitmap
                        publishMediaSessionState()
                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        try { manager?.notify(NOTIFICATION_ID,
                            buildNotification(true, currentTitle, currentArtist)) }
                        catch (e: SecurityException) { Log.w(TAG, "Artwork notification denied", e) }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Track cover is unavailable; fallback icon kept", e)
            } finally { connection?.disconnect() }
        }.start()
    }

    private fun publishMediaSessionState() {
        val s = mediaSession ?: return
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP
        if (playbackDurationMs > 0L) actions = actions or PlaybackState.ACTION_SEEK_TO
        if (canPrevious) actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        if (canNext) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        s.setPlaybackState(PlaybackState.Builder()
            .setActions(actions)
            .setState(state, playbackPositionMs, if (isPlaying) 1.0f else 0.0f)
            .build())
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, currentArtist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "LESKOV")
        if (playbackDurationMs > 0L) metadata.putLong(MediaMetadata.METADATA_KEY_DURATION, playbackDurationMs)
        val image = artworkBitmap ?: LeskovNotificationBrand.logo(this)
        if (image != null) metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, image)
        s.setMetadata(metadata.build())
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

        val largeIconBitmap = LeskovNotificationBrand.logo(this)

        val previousPendingIntent = PendingIntent.getService(
            this, 4, Intent(this, LeskovMediaService::class.java).apply { action = ACTION_USER_PREVIOUS },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextPendingIntent = PendingIntent.getService(
            this, 5, Intent(this, LeskovMediaService::class.java).apply { action = ACTION_USER_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText("LESKOV")
            .setSmallIcon(R.drawable.ic_notification_leskov)
            .setContentIntent(openPendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOngoing(playing)
            .setShowWhen(false)
            .setColor(0xFF9B8CFB.toInt())

        // Show arrows only when the website has confirmed its playlist supports them.
        if (canPrevious) builder.addAction(android.R.drawable.ic_media_previous, "Назад", previousPendingIntent)
        val toggleIndex = if (canPrevious) 1 else 0
        builder.addAction(playPauseIcon, playPauseTitle, togglePendingIntent)
        if (canNext) builder.addAction(android.R.drawable.ic_media_next, "Далее", nextPendingIntent)
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Закрыть", stopPendingIntent)
        builder.setStyle(Notification.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(toggleIndex))

        builder.setLargeIcon(largeIconBitmap)
        return builder.build()
    }

    override fun onDestroy() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(noisyReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        resetReportedState()
        stopPlayback()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LeskovMediaService"

        const val CHANNEL_ID = "leskov_playback_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_PLAY = "com.example.media.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.media.ACTION_PAUSE"
        const val ACTION_PROGRESS = "com.example.media.ACTION_PROGRESS"
        const val ACTION_USER_PREVIOUS = "com.example.media.ACTION_USER_PREVIOUS"
        const val ACTION_USER_NEXT = "com.example.media.ACTION_USER_NEXT"
        const val ACTION_USER_TOGGLE_PLAY_PAUSE = "com.example.media.ACTION_USER_TOGGLE_PLAY_PAUSE"
        const val ACTION_USER_STOP = "com.example.media.ACTION_USER_STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_ARTWORK = "extra_artwork"
        const val EXTRA_POSITION = "extra_position_ms"
        const val EXTRA_DURATION = "extra_duration_ms"
        const val EXTRA_CAN_PREVIOUS = "extra_can_previous"
        const val EXTRA_CAN_NEXT = "extra_can_next"

        // State deduplication to prevent spurious intent churning
        private var lastReportedState: Boolean? = null
        private var lastReportedTitle: String? = null
        private var lastReportedArtist: String? = null
        private var lastReportedArtwork: String? = null
        private var lastReportedPosition: Long = -1L
        private var lastReportedDuration: Long = -1L
        private var lastReportedCanPrevious = false
        private var lastReportedCanNext = false

        // Callback listeners for webview action binding
        var onMediaActionRequested: ((action: String) -> Unit)? = null

        fun notifyWebClientsPause() {
            onMediaActionRequested?.invoke("pause")
        }

        fun notifyWebClientsPlay() {
            onMediaActionRequested?.invoke("play")
        }
        fun notifyWebClientsNext() { onMediaActionRequested?.invoke("next") }
        fun notifyWebClientsPrevious() { onMediaActionRequested?.invoke("previous") }
        fun notifyWebClientsSeek(positionMs: Long) { onMediaActionRequested?.invoke("seek:${positionMs.coerceAtLeast(0L)}") }

        fun updatePlayback(context: Context, isPlaying: Boolean, title: String?, artist: String?,
                           artwork: String? = null, positionMs: Long = 0L, durationMs: Long = 0L,
                           hasPrevious: Boolean = false, hasNext: Boolean = false) {
            // Idempotent state deduplication: do not churn if state didn't change
            val metadataSame = lastReportedState == isPlaying && lastReportedTitle == title &&
                lastReportedArtist == artist && lastReportedArtwork == artwork
            val progressChanged = durationMs != lastReportedDuration ||
                kotlin.math.abs(positionMs - lastReportedPosition) >= 2000L ||
                hasPrevious != lastReportedCanPrevious || hasNext != lastReportedCanNext
            if (metadataSame && !progressChanged) return
            val previousState = lastReportedState
            lastReportedState = isPlaying
            lastReportedTitle = title
            lastReportedArtist = artist
            lastReportedArtwork = artwork
            lastReportedPosition = positionMs
            lastReportedDuration = durationMs
            lastReportedCanPrevious = hasPrevious
            lastReportedCanNext = hasNext

            // Do not launch a background Service for an idle page or a redundant pause.
            // A pause is only meaningful if an already-running media notification existed.
            if (!isPlaying && previousState != true) return

            try {
                val intent = Intent(context, LeskovMediaService::class.java).apply {
                    action = if (isPlaying && metadataSame) ACTION_PROGRESS else if (isPlaying) ACTION_PLAY else ACTION_PAUSE
                    putExtra(EXTRA_TITLE, title ?: "LESKOV")
                    putExtra(EXTRA_ARTIST, artist ?: "Igor Leskov")
                    putExtra(EXTRA_ARTWORK, artwork.orEmpty())
                    putExtra(EXTRA_POSITION, positionMs)
                    putExtra(EXTRA_DURATION, durationMs)
                    putExtra(EXTRA_CAN_PREVIOUS, hasPrevious)
                    putExtra(EXTRA_CAN_NEXT, hasNext)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPlaying && !metadataSame) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send updatePlayback intent", e)
            }
        }

        private fun resetReportedState() {
            lastReportedState = null
            lastReportedTitle = null
            lastReportedArtist = null
            lastReportedArtwork = null
            lastReportedPosition = -1L
            lastReportedDuration = -1L
            lastReportedCanPrevious = false
            lastReportedCanNext = false
        }

        fun stopSilently(context: Context) {
            resetReportedState()
            context.stopService(Intent(context, LeskovMediaService::class.java))
        }

        fun stop(context: Context) {
            lastReportedState = null
            lastReportedTitle = null
            lastReportedArtist = null
            lastReportedArtwork = null
            lastReportedPosition = -1L
            lastReportedDuration = -1L
            lastReportedCanPrevious = false
            lastReportedCanNext = false
            try {
                val intent = Intent(context, LeskovMediaService::class.java).apply {
                    action = ACTION_USER_STOP
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }
}
