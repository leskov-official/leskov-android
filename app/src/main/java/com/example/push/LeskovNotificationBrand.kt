package com.example.push

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.R
import kotlin.math.roundToInt

object LeskovNotificationBrand {
    private var cached: Bitmap? = null
    /** Bounded, padded artwork prevents the round logo being cropped into the system template. */
    @Synchronized
    fun logo(context: Context): Bitmap {
        val size = (48 * context.resources.displayMetrics.density).roundToInt().coerceIn(48, 192)
        cached?.takeIf { !it.isRecycled && it.width == size }?.let { return it }
        val source = BitmapFactory.decodeResource(context.resources, R.drawable.leskov_logo,
            BitmapFactory.Options().apply { inScaled = false })
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val inset = size * 0.12f
        Canvas(output).apply {
            val clip = Path().apply { addCircle(size / 2f, size / 2f, size / 2f - inset, Path.Direction.CW) }
            save(); clipPath(clip)
            drawBitmap(source, null, RectF(inset, inset, size - inset, size - inset),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            restore()
        }
        cached = output
        return output
    }
}
