package com.harish.wordlookup.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.harish.wordlookup.R
import com.harish.wordlookup.WordLookupApp
import com.harish.wordlookup.data.Languages
import com.harish.wordlookup.data.WordCandidate
import com.harish.wordlookup.data.WordOfDay
import com.harish.wordlookup.data.review.ReviewCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The 2x2 "Word of the day" home-screen widget. Classic `RemoteViews` (no
 * Glance dependency - see CLAUDE.md's Round 11 section for why). Tapping the
 * whole tile cycles to the next candidate word (per-widget-instance offset in
 * [android.content.SharedPreferences], the standard widget-state pattern -
 * no Room schema change); there is no separate body-tap-to-open-app zone,
 * matching the mockup's own "tap cycles the word" copy.
 */
class WordOfDayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_CYCLE_WORD) return
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (appWidgetId == -1) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val current = prefs.getInt(appWidgetId.toString(), 0)
                prefs.edit().putInt(appWidgetId.toString(), current + 1).apply()
                updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val app = context.applicationContext as WordLookupApp
        val language = app.targetLanguageState.value
        val (all, overdue) = candidates(app)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val offset = prefs.getInt(appWidgetId.toString(), 0)

        val views = RemoteViews(context.packageName, R.layout.widget_word_of_day)
        views.setImageViewBitmap(R.id.widget_glyph, WidgetGlyphRenderer.glyphBitmap(Languages.get(language).glyph))

        val pool = overdue.ifEmpty { all }
        val picked = if (pool.isEmpty()) null else pool[(WordOfDay.dayIndex() + offset).mod(pool.size)]

        if (picked == null) {
            views.setViewVisibility(R.id.widget_placeholder, View.VISIBLE)
            views.setViewVisibility(R.id.widget_word, View.GONE)
            views.setViewVisibility(R.id.widget_translation, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_placeholder, View.GONE)
            views.setViewVisibility(R.id.widget_word, View.VISIBLE)
            views.setViewVisibility(R.id.widget_translation, View.VISIBLE)
            views.setTextViewText(R.id.widget_word, picked.result.original)
            views.setTextViewText(R.id.widget_translation, picked.result.translation)
        }

        val cycleIntent = Intent(context, WordOfDayWidgetProvider::class.java).apply {
            action = ACTION_CYCLE_WORD
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val cyclePendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            cycleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, cyclePendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    /** [WordOfDay.pick]'s two pools, built from the real register - the overdue query already exists for the quiz. */
    private suspend fun candidates(app: WordLookupApp): Pair<List<WordCandidate>, List<WordCandidate>> {
        val all = app.repository.observeRegister().first().map { WordCandidate(it.result, it.language) }
        val overdue = app.repository.loadDueBatch(limit = all.size.coerceAtLeast(1)).map { card: ReviewCard ->
            WordCandidate(card.result, card.language)
        }
        return all to overdue
    }

    companion object {
        const val ACTION_CYCLE_WORD = "com.harish.wordlookup.action.CYCLE_WORD"
        private const val PREFS_NAME = "widget_word_of_day_state"

        /** Called from [MainViewModel][com.harish.wordlookup.ui.MainViewModel]'s language-change and save/delete paths - repaints every placed instance, not just the one the alarm would eventually reach. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WordOfDayWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val intent = Intent(context, WordOfDayWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
