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
import com.harish.wordlookup.data.DigestWeek
import com.harish.wordlookup.data.Languages
import com.harish.wordlookup.data.WordCandidate
import com.harish.wordlookup.data.WordOfDay
import com.harish.wordlookup.ui.MainActivity
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The 4x2 wide home-screen widget. Shows word-of-day every day except
 * Friday, when it shows the week's new-word count instead - the same
 * Friday cadence [DigestReminderScheduler] already uses for the notification,
 * computed fresh here rather than shared state, since this is a plain
 * day-of-week check with no scheduling of its own. No tap-to-flip: the
 * digest is a passive, always-computed fact (round 10's own reasoning for
 * why the digest itself has no manual toggle), not a user-chosen state, so
 * there's no per-instance state to store here unlike the 2x2 widget's cycle
 * offset. Tapping the tile opens the app on Home.
 */
class DigestWidgetProvider : AppWidgetProvider() {

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

    private suspend fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val app = context.applicationContext as WordLookupApp
        val language = app.targetLanguageState.value
        val register = app.repository.observeRegister().first()

        val views = RemoteViews(context.packageName, R.layout.widget_digest)
        views.setImageViewBitmap(R.id.widget_glyph, WidgetGlyphRenderer.glyphBitmap(Languages.get(language).glyph))

        val isFriday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
        if (isFriday) {
            val since = DigestWeek.startOfWeekMillis()
            val count = register.count { it.createdAtMillis >= since }
            val word = if (count == 1) "word" else "words"
            views.setTextViewText(R.id.widget_label, "This week")
            views.setViewVisibility(R.id.widget_placeholder, View.GONE)
            views.setViewVisibility(R.id.widget_word, View.VISIBLE)
            views.setViewVisibility(R.id.widget_translation, View.VISIBLE)
            views.setTextViewText(R.id.widget_word, "$count new $word")
            views.setTextViewText(R.id.widget_translation, DigestWeek.rangeLabel(since))
        } else {
            views.setTextViewText(R.id.widget_label, "Word of the day")
            val all = register.map { WordCandidate(it.result, it.language) }
            val overdue = app.repository.loadDueBatch(limit = all.size.coerceAtLeast(1))
                .map { WordCandidate(it.result, it.language) }
            val picked = WordOfDay.pick(all, overdue)
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
        }

        val openIntent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        /** Called from the same trigger points as [WordOfDayWidgetProvider.updateAll]. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DigestWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val intent = Intent(context, DigestWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
