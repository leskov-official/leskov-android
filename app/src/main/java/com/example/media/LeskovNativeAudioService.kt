package com.example.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.R
import com.example.BuildConfig
import com.example.model.LeskovConstants
import org.json.JSONArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NativeAudioState(
    val active: Boolean = false,
    val playing: Boolean = false,
    val title: String = "LESKOV",
    val artist: String = "Igor Leskov",
    val buffering: Boolean = false,
    val error: String? = null
)

/**
 * Real, independent music playback for the first-party HTTPS streams already authorized by
 * the user's PHP session. Used for hand-off from WebView before navigation/home, not for video,
 * blob URLs, calls or premium bypass. The browser pauses BEFORE Media3 requests audio focus.
 */
@OptIn(UnstableApi::class)
class LeskovNativeAudioService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private var streamFailed = false
    private lateinit var httpFactory: DefaultHttpDataSource.Factory

    override fun onCreate() {
        super.onCreate()
        httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(false)
            .setUserAgent("LESKOV-Android/${BuildConfig.VERSION_NAME}")
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                setHandleAudioBecomingNoisy(true)
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "Native audio stream failed (${error.errorCodeName}); playback stopped", error)
                        streamFailed = true
                        active = false
                        player.stop()
                        player.clearMediaItems()
                        _state.update { it.copy(active = false, playing = false,
                            buffering = false, error = "stream_unavailable") }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        _state.update { it.copy(
                            title = mediaItem?.mediaMetadata?.title?.toString() ?: "LESKOV",
                            artist = mediaItem?.mediaMetadata?.artist?.toString() ?: "Igor Leskov"
                        ) }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _state.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING) }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        // A paused media session is still available from the notification.
                        if (!streamFailed) {
                            active = isPlaying || player.mediaItemCount > 0
                            _state.update { it.copy(active = active, playing = isPlaying) }
                        }
                    }
                })
            }
        session = MediaSession.Builder(this, player).build()
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId("leskov_native_audio")
            .setChannelName(R.string.media_channel_name)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_notification_leskov)
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HANDOFF -> {
                val raw = intent.getStringExtra(EXTRA_URL)
                val uri = try { raw?.let { Uri.parse(it) } } catch (_: Exception) { null }
                if (uri == null || !isAllowedAudioUri(uri)) {
                    Log.w(TAG, "Rejected non-first-party media URL")
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                val url = uri.toString()
                val cookie = CookieManager.getInstance().getCookie(url).orEmpty()
                val requestHeaders = mutableMapOf("Referer" to LeskovConstants.BASE_URL)
                if (cookie.isNotBlank()) requestHeaders["Cookie"] = cookie
                httpFactory.setDefaultRequestProperties(requestHeaders)

                val title = intent.getStringExtra(EXTRA_TITLE)?.take(160).orEmpty().ifBlank { "LESKOV" }
                val artist = intent.getStringExtra(EXTRA_ARTIST)?.take(160).orEmpty().ifBlank { "Igor Leskov" }
                val positionMs = intent.getLongExtra(EXTRA_POSITION_MS, 0L).coerceAtLeast(0L)
                val remoteArtwork = intent.getStringExtra(EXTRA_ARTWORK)?.let { raw ->
                    try { Uri.parse(raw) } catch (_: Exception) { null }
                }?.takeIf { isAllowedAudioUri(it) }
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist(artist)
                            .setArtworkUri(remoteArtwork ?: Uri.parse("android.resource://$packageName/${R.drawable.leskov_logo}"))
                            .build()
                    )
                    .build()
                streamFailed = false
                val queue = parseQueue(intent.getStringExtra(EXTRA_QUEUE),
                    Uri.parse("android.resource://$packageName/${R.drawable.leskov_logo}"))
                val queueIndex = intent.getIntExtra(EXTRA_QUEUE_INDEX, 0)
                val validQueue = queue.isNotEmpty() && queueIndex in queue.indices &&
                    queue[queueIndex].localConfiguration?.uri == uri
                if (validQueue) {
                    player.setMediaItems(queue, queueIndex, positionMs)
                    val mode = intent.getStringExtra(EXTRA_MODE)
                    player.shuffleModeEnabled = mode == "shuffle"
                    player.repeatMode = if (mode == "repeat") Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
                } else {
                    player.shuffleModeEnabled = false
                    player.repeatMode = Player.REPEAT_MODE_OFF
                    player.setMediaItem(mediaItem, positionMs)
                }
                _state.value = NativeAudioState(active = true, playing = false, title = title,
                    artist = artist, buffering = true)
                player.prepare()
                player.play()
                active = true
                // Remove the earlier legacy WebView state notification to prevent duplicates.
                LeskovMediaService.stopSilently(this)
                return START_STICKY
            }
            ACTION_TOGGLE -> {
                if (player.playWhenReady) player.pause() else player.play()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                streamFailed = false
                active = false
                _state.value = NativeAudioState()
                player.stop()
                player.clearMediaItems()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        active = false
        _state.value = NativeAudioState()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LeskovNativeAudio"
        private const val ACTION_HANDOFF = "com.aistudio.leskov.HANDOFF_AUDIO"
        private const val ACTION_STOP = "com.aistudio.leskov.STOP_NATIVE_AUDIO"
        private const val ACTION_TOGGLE = "com.aistudio.leskov.TOGGLE_NATIVE_AUDIO"
        private const val EXTRA_URL = "stream_uri"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTIST = "artist"
        private const val EXTRA_POSITION_MS = "position_ms"
        private const val EXTRA_ARTWORK = "artwork_url"
        private const val EXTRA_QUEUE = "authorized_queue"
        private const val EXTRA_QUEUE_INDEX = "queue_index"
        private const val EXTRA_MODE = "queue_mode"
        @Volatile private var active = false
        private val _state = MutableStateFlow(NativeAudioState())
        val state = _state.asStateFlow()
        fun isActive(): Boolean = active

        fun isAllowedAudioUri(uri: Uri?): Boolean = uri != null &&
            uri.scheme.equals("https", true) && uri.host.equals(LeskovConstants.HOST, true) &&
            uri.userInfo == null && uri.port in listOf(-1, 443)

        internal fun parseQueue(raw: String?, fallbackArtwork: Uri): List<MediaItem> {
            if (raw == null || raw.length > 600_000) return emptyList()
            return try {
                val json = JSONArray(raw)
                if (json.length() !in 1..100) emptyList()
                else (0 until json.length()).map { index ->
                    val track = json.getJSONObject(index)
                    val uri = Uri.parse(track.getString("url"))
                    require(isAllowedAudioUri(uri))
                    val artwork = track.optString("artwork").takeIf { it.isNotBlank() }
                        ?.let { Uri.parse(it) }?.takeIf { isAllowedAudioUri(it) }
                    MediaItem.Builder().setUri(uri).setMediaMetadata(
                        MediaMetadata.Builder().setTitle(track.optString("title", "LESKOV").take(160))
                            .setArtist(track.optString("artist", "Igor Leskov").take(160))
                            .setArtworkUri(artwork ?: fallbackArtwork)
                            .build()
                    ).build()
                }
            } catch (_: Exception) { emptyList() }
        }

        /** Called only after an actual user-started audio element is playing. */
        fun handoff(context: Context, url: String, title: String, artist: String,
                    artwork: String, positionMs: Long, queueJson: String? = null,
                    queueIndex: Int = 0, mode: String = "sequential"): Boolean {
            val uri = try { Uri.parse(url) } catch (_: Exception) { null }
            if (!isAllowedAudioUri(uri)) return false
            return try {
                val intent = Intent(context, LeskovNativeAudioService::class.java).apply {
                    action = ACTION_HANDOFF
                    putExtra(EXTRA_URL, uri.toString())
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_ARTIST, artist)
                    putExtra(EXTRA_ARTWORK, artwork)
                    putExtra(EXTRA_POSITION_MS, positionMs)
                    putExtra(EXTRA_QUEUE, queueJson?.takeIf { it.length <= 600_000 })
                    putExtra(EXTRA_QUEUE_INDEX, queueIndex)
                    putExtra(EXTRA_MODE, mode)
                }
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Unable to start native audio handoff", e)
                false
            }
        }

        fun toggle(context: Context) {
            if (!active) return
            try {
                context.startService(Intent(context, LeskovNativeAudioService::class.java).apply {
                    action = ACTION_TOGGLE
                })
            } catch (e: Exception) { Log.w(TAG, "Unable to toggle native playback", e) }
        }

        fun stop(context: Context) {
            if (!active) return
            active = false
            try {
                context.startService(Intent(context, LeskovNativeAudioService::class.java).apply {
                    action = ACTION_STOP
                })
            } catch (e: Exception) {
                Log.w(TAG, "Could not stop native audio service", e)
            }
        }
    }
}
