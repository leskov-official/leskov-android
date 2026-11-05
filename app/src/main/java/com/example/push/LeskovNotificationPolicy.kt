package com.example.push

import android.net.Uri
import com.example.web.WebNavigation

object LeskovNotificationPolicy {
    fun target(type: String, rawUrl: String?, data: Map<String, String> = emptyMap()): Uri {
        val raw = rawUrl?.trim().orEmpty()
        val parsed = raw.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (WebNavigation.isTrustedUri(parsed)) return parsed!!
        if (parsed != null && parsed.scheme == null && parsed.authority == null && !raw.startsWith("\\")) {
            val relative = Uri.parse("https://lesv.online/" + raw.trimStart('/'))
            if (WebNavigation.isTrustedUri(relative)) return relative
        }
        val path = when (type.lowercase()) {
            "message", "message_new", "chat", "direct" -> "messages.php"
            "call", "incoming_call", "call_missed" -> if (data["engine"] == "messages") "messages.php" else "profile.php?section=calls"
            else -> "notifications.php"
        }
        return Uri.parse("https://lesv.online/$path").buildUpon().apply {
            val peer = (data["sender_id"] ?: data["peer_id"])?.toLongOrNull()
            if (path == "messages.php" && peer != null && peer > 0) appendQueryParameter("user", peer.toString())
        }.build()
    }

    fun channel(type: String): String = when (type.lowercase()) {
        "call", "incoming_call", "call_missed" -> FcmTokenManager.CHANNEL_CALLS
        "message", "message_new", "chat", "direct" -> FcmTokenManager.CHANNEL_MESSAGES
        "social", "reaction", "like", "friend", "friend_request", "gift" -> FcmTokenManager.CHANNEL_SOCIAL
        "post", "release", "music", "group_post" -> FcmTokenManager.CHANNEL_POSTS
        else -> FcmTokenManager.CHANNEL_SYSTEM
    }
}
