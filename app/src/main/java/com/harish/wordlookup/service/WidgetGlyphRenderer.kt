package com.harish.wordlookup.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Draws a language glyph onto a small ink-square bitmap - the same identity
 * mark the launcher icon and the in-app brand mark already carry. Extracted
 * from [LookupTileService]'s own private `glyphBitmap` (round 6) so the
 * round-11 home-screen widgets can render the identical mark: `RemoteViews`
 * can only host primitive views (`ImageView.setImageBitmap`, not an arbitrary
 * Canvas/Compose draw), so a bitmap is the only way to get this glyph onto a
 * widget at all, same as it was the only way onto the Quick Settings tile.
 */
internal object WidgetGlyphRenderer {
    fun glyphBitmap(glyph: String, sizePx: Int = 96): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#16181C") }
        canvas.drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), sizePx * 0.3f, sizePx * 0.3f, bg)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = sizePx * 0.5f
            textAlign = Paint.Align.CENTER
        }
        val textY = sizePx / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(glyph, sizePx / 2f, textY, text)
        return bitmap
    }
}
