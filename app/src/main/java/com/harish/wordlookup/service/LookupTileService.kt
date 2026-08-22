package com.harish.wordlookup.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.harish.wordlookup.R
import com.harish.wordlookup.WordLookupApp
import com.harish.wordlookup.data.Languages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Switches the **instant popup** (the no-tap accessibility overlay) on and
 * off - not the whole app. Turning it off leaves "Word Lookup" in the text
 * selection menu, so the tile reads as "instant lookups on/off" rather than
 * "app on/off", which is what a one-tap toggle is actually useful for.
 */
class LookupTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var collectJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val app = application as WordLookupApp
        collectJob = scope.launch {
            combine(app.instantEnabledState, app.targetLanguageState) { enabled, language ->
                enabled to language
            }.collect { (enabled, language) -> syncTile(enabled, language) }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        collectJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onClick() {
        super.onClick()
        // toggleInstant() read-modify-writes DataStore atomically - the real
        // stored value, never a StateFlow snapshot, which is seeded with a
        // hardcoded default on cold start and can be briefly stale. Computing
        // the next value from that snapshot was the original tile bug: it could
        // write the value that was already stored, making the first tap look
        // like it did nothing. The visible tile updates from the
        // onStartListening collector once the write lands.
        val app = application as WordLookupApp
        scope.launch { app.settings.toggleInstant() }
    }

    private fun syncTile(instantEnabled: Boolean, language: String) {
        val tile = qsTile ?: return
        tile.state = if (instantEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        // Label stays the app name so the tile is findable in the QS picker
        // (the manifest and Permissions.requestAddTile use the same resource);
        // the subtitle is what says which switch this actually is.
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (instantEnabled) "Instant on" else "Instant off"
        }
        tile.icon = Icon.createWithBitmap(glyphBitmap(Languages.get(language).glyph))
        tile.updateTile()
    }

    /**
     * Draws the active language's glyph onto a small ink-square bitmap, so
     * the tile carries the same identity as the in-app brand mark and the
     * launcher icon rather than a fixed generic glyph. Full-colour bitmap
     * icons are shown as-is in Quick Settings (unlike monochrome vector
     * icons, which the system re-tints), so this renders on-brand regardless
     * of the tile's active/inactive state.
     */
    private fun glyphBitmap(glyph: String): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#16181C") }
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.3f, size * 0.3f, bg)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size * 0.5f
            textAlign = Paint.Align.CENTER
        }
        val textY = size / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(glyph, size / 2f, textY, text)
        return bitmap
    }
}
