package com.harish.wordlookup.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.harish.wordlookup.R
import com.harish.wordlookup.WordLookupApp
import com.harish.wordlookup.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires on [ACTION_FIRE] (from [ReviewReminderScheduler]'s alarm) and on
 * `ACTION_BOOT_COMPLETED` (an inexact `AlarmManager` alarm does not survive
 * a reboot - without re-arming here, a reminder the user turned on would
 * silently stop after the phone next restarts).
 *
 * The "must not be annoying" rules live here, not in the scheduler: this is
 * where the actual decision to post (or not) gets made.
 * - Zero due -> posts nothing, just re-arms for tomorrow. No "you haven't
 *   opened the app" nag.
 * - `IMPORTANCE_LOW`: no sound, no vibration, no heads-up banner.
 * - `setAutoCancel(true)`, tapping opens Review directly via
 *   [MainActivity.EXTRA_OPEN_REVIEW] - no re-notify, no badge.
 */
class ReviewReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_FIRE, Intent.ACTION_BOOT_COMPLETED -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        handle(context, intent.action == ACTION_FIRE)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private suspend fun handle(context: Context, shouldNotify: Boolean) {
        val app = context.applicationContext as WordLookupApp
        val enabled = app.settings.reminderEnabled.first()
        if (!enabled) return

        if (shouldNotify) {
            val due = app.repository.observeDueCount().first()
            if (due > 0) postNotification(context, due)
        }

        // Re-arm for tomorrow regardless of whether this fire notified -
        // both the daily alarm and a boot both need the next occurrence
        // scheduled, or the reminder silently stops.
        val hour = app.settings.reminderHour.first()
        ReviewReminderScheduler.schedule(context, hour)
    }

    private fun postNotification(context: Context, dueCount: Int) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Review reminder", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Once a day, only when saved words are due for review."
            },
        )

        val openIntent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_REVIEW, true)
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val word = if (dueCount == 1) "word" else "words"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("$dueCount $word ready to review")
            .setContentText("Tap to open your quiz.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_FIRE = "com.harish.wordlookup.action.REVIEW_REMINDER_FIRE"
        private const val CHANNEL_ID = "review_reminder"
        private const val NOTIFICATION_ID = 8100
        private const val REQUEST_CODE = 8101
    }
}
