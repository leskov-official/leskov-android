package com.example.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class LeskovFirebaseMessagingService : FirebaseMessagingService() {

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM registration token received (value redacted)")
        FcmTokenManager.saveToken(this, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        // Ensure notification channels are registered
        FcmTokenManager.createNotificationChannels(this, FcmTokenManager.savedLanguage(this))

        val data = remoteMessage.data
        val notification = remoteMessage.notification
        val collapseId = data["collapse_id"]
        val callPrefs = getSharedPreferences("leskov_call_cancellations", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        callPrefs.all.filterValues { it is Long && it < now }.keys.forEach {
            callPrefs.edit().remove(it).apply()
        }
        if (data["type"] == "call_cancelled") {
            collapseId?.let {
                callPrefs.edit().putLong(it, now + 300_000L).apply()
                (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(it.hashCode())
            }
            return
        }
        if (collapseId != null && callPrefs.getLong(collapseId, 0L) > now) return
        val expiresAt = data["expires_at"]?.toLongOrNull()
        if (data["type"] in listOf("call", "incoming_call") &&
            expiresAt != null && expiresAt <= System.currentTimeMillis() / 1000L) return


        val type = data["type"] ?: "system"
        val title = (data["title"] ?: notification?.title ?: "LESKOV").take(160)
        val body = (data["body"] ?: notification?.body ?: "").take(3000)
        val targetUrl = data["target_url"] ?: data["url"]
        val collapseKey = data["collapse_id"] ?: remoteMessage.collapseKey

        showNotification(type, title, body, targetUrl, collapseKey, data)
    }

    private fun showNotification(
        type: String,
        title: String,
        body: String,
        rawUrl: String?,
        collapseKey: String?,
        extraData: Map<String, String>
    ) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // Validate and sanitize internal URL
        val targetUri = LeskovNotificationPolicy.target(type, rawUrl, extraData)
        val notificationId = collapseKey?.hashCode() ?: (type + ":" + targetUri.toString() + ":" + title).hashCode()
        val clickIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            this.data = targetUri
        }
        val pendingIntent = PendingIntent.getActivity(this, notificationId, clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val largeIconBitmap = LeskovNotificationBrand.logo(this)
        val channelId = LeskovNotificationPolicy.channel(type)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            // CRITICAL: Monochrome white silhouette vector drawable on transparent background
            .setSmallIcon(R.drawable.ic_notification_leskov)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFF527FF5.toInt()) // LESKOV Accent glow brand tint

        if (largeIconBitmap != null) {
            notificationBuilder.setLargeIcon(largeIconBitmap)
        }

        notificationBuilder.setSubText(nm.getNotificationChannel(channelId)?.name)
        notificationBuilder.setShowWhen(true).setWhen(System.currentTimeMillis())
            .setOnlyAlertOnce(true).setStyle(NotificationCompat.BigTextStyle().bigText(body))

        when (channelId) {
            FcmTokenManager.CHANNEL_CALLS -> {
                notificationBuilder
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(false)
                    .setTimeoutAfter(45_000L)

                // Deliberately no fake "Accept" action: the real call answer requires
                // a verified call ID, an authenticated server event and a WebRTC handshake.
                // Tapping the notification opens LESKOV to handle the pending call.

            }
            FcmTokenManager.CHANNEL_MESSAGES -> {
                notificationBuilder
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    // Protect private messages on lock screen
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            }
            FcmTokenManager.CHANNEL_POSTS, FcmTokenManager.CHANNEL_SOCIAL -> {
                notificationBuilder
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            }
            else -> {
                notificationBuilder
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            }
        }

        try {
            nm.notify(notificationId, notificationBuilder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Notifications are disabled or permission not granted", e)
        }
    }

    companion object {
        private const val TAG = "LeskovPushService"
    }
}
