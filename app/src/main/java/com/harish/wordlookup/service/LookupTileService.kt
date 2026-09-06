package com.harish.wordlookup.service

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
        // Full-colour bitmap icons are shown as-is in Quick Settings (unlike
        // monochrome vector icons, which the system re-tints), so this
        // renders on-brand regardless of the tile's active/inactive state.
        tile.icon = Icon.createWithBitmap(WidgetGlyphRenderer.glyphBitmap(Languages.get(language).glyph))
        tile.updateTile()
    }
}
