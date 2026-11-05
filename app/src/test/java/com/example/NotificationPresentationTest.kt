package com.example

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.push.FcmTokenManager
import com.example.push.LeskovFirebaseMessagingService
import com.example.push.LeskovNotificationBrand
import com.example.push.LeskovNotificationPolicy
import com.google.firebase.messaging.RemoteMessage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationPresentationTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun `notification targets preserve internal details and reject external links`() {
        assertEquals("https://lesv.online/messages.php?user=42#message-7",
            LeskovNotificationPolicy.target("message", "https://lesv.online/messages.php?user=42#message-7").toString())
        assertEquals("https://lesv.online/feed.php#post-8",
            LeskovNotificationPolicy.target("post", "/feed.php#post-8").toString())
        for (url in listOf(null, "https://evil.test/a", "javascript:alert(1)", "https://user:pass@lesv.online/a")) {
            assertEquals("https://lesv.online/notifications.php", LeskovNotificationPolicy.target("system", url).toString())
        }
        assertEquals("https://lesv.online/messages.php?user=42",
            LeskovNotificationPolicy.target("message", null, mapOf("sender_id" to "42")).toString())
        assertEquals(FcmTokenManager.CHANNEL_MESSAGES, LeskovNotificationPolicy.channel("message_new"))
        assertEquals(FcmTokenManager.CHANNEL_POSTS, LeskovNotificationPolicy.channel("group_post"))
    }

    @Test fun `notification logo is bounded and has transparent safe margins`() {
        val logo = LeskovNotificationBrand.logo(app)
        assertTrue(logo.width in 48..192)
        assertEquals(logo.width, logo.height)
        assertEquals(0, Color.alpha(logo.getPixel(0, 0)))
        assertEquals(0, Color.alpha(logo.getPixel(logo.width - 1, logo.height - 1)))
    }

    @Test fun `received push opens its own route with expandable text and the branded small icon`() {
        val service = Robolectric.buildService(LeskovFirebaseMessagingService::class.java).create().get()
        val message = RemoteMessage.Builder("fixture")
            .addData("type", "message").addData("title", "New message")
            .addData("body", "A long message body").addData("collapse_id", "fixture-message-42")
            .addData("target_url", "https://lesv.online/messages.php?user=42").build()
        service.onMessageReceived(message)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = shadowOf(manager).allNotifications.single()
        assertEquals(FcmTokenManager.CHANNEL_MESSAGES, posted.channelId)
        assertEquals(Notification.VISIBILITY_PRIVATE, posted.visibility)
        assertEquals("A long message body", posted.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
        assertEquals("https://lesv.online/messages.php?user=42", shadowOf(posted.contentIntent).savedIntent.data.toString())
        assertEquals(R.drawable.ic_notification_leskov, posted.smallIcon.resId)
        assertNull(posted.group)
    }
}
