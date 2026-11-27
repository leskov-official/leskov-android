package com.example.push

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.example.R
import kotlin.math.roundToInt

object LeskovNotificationBrand {
    private var cached: Bitmap? = null

    fun senderAvatar(raw: String?): Bitmap? {
        val uri = raw?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() } ?: return null
        if (!com.example.web.WebNavigation.isTrustedUri(uri)) return null
        // Only public image paths; never fetch arbitrary application endpoints.
        val path = uri.path.orEmpty()
        val public = path.startsWith("/uploads/") && uri.query == null
        // The existing server media endpoint authorizes each private object. It permits
        // public profile avatars but refuses anonymous access to private message files.
        val protectedFile = if (path == "/media.php" && uri.queryParameterNames == setOf("file"))
            uri.getQueryParameter("file")?.takeIf { it.startsWith("private_uploads/user_uploads/") } else null
        val imagePath = if (public) path else protectedFile ?: return null
        if (uri.fragment != null || imagePath.contains("..") || imagePath.contains('\\') ||
            imagePath.substringAfterLast('.').lowercase(java.util.Locale.ROOT) !in setOf("png", "jpg", "jpeg", "webp", "gif")) return null
        return runCatching {
            val avatarUri = if (protectedFile != null) uri.buildUpon().appendQueryParameter("w", "160").build() else uri
            val connection = java.net.URL(avatarUri.toString()).openConnection() as javax.net.ssl.HttpsURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 1500; connection.readTimeout = 1500
                connection.useCaches = false
                if (connection.responseCode != 200 || !connection.contentType.orEmpty().startsWith("image/")) return null
                val limit = 512 * 1024
                val deadline = android.os.SystemClock.elapsedRealtime() + 2000L
                val bytes = connection.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192)
                    while (true) {
                        if (android.os.SystemClock.elapsedRealtime() > deadline) return null
                        val count = input.read(buf); if (count < 0) break
                        if (out.size() + count > limit) return null
                        out.write(buf, 0, count)
                    }
                    out.toByteArray()
                }
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                if (opts.outWidth !in 1..8192 || opts.outHeight !in 1..8192) return null
                var sample = 1
                while (maxOf(opts.outWidth, opts.outHeight) / sample > 256) sample *= 2
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
                val edge = minOf(decoded.width, decoded.height)
                val square = Bitmap.createBitmap(decoded, (decoded.width-edge)/2, (decoded.height-edge)/2, edge, edge)
                Bitmap.createScaledBitmap(square, 128, 128, true)
            } finally { connection.disconnect() }
        }.getOrNull()
    }
    /** Bounded, padded artwork prevents the round logo being cropped into the system template. */
    @Synchronized
    fun logo(context: Context): Bitmap {
        val size = (48 * context.resources.displayMetrics.density).roundToInt().coerceIn(48, 192)
        cached?.takeIf { !it.isRecycled && it.width == size }?.let { return it }
        val source = BitmapFactory.decodeResource(context.resources, R.drawable.leskov_application_icon,
            BitmapFactory.Options().apply { inScaled = false })
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val inset = size * 0.025f
        Canvas(output).apply {
            drawBitmap(source, null, RectF(inset, inset, size - inset, size - inset),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        cached = output
        return output
    }
}
