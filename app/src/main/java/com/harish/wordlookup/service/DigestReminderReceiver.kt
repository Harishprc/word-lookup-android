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
import com.harish.wordlookup.data.DigestWeek
import com.harish.wordlookup.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires on [ACTION_FIRE_DIGEST] (from [DigestReminderScheduler]'s Friday
 * alarm) and on `ACTION_BOOT_COMPLETED` (an inexact alarm does not survive a
 * reboot, same reasoning as [ReviewReminderReceiver]).
 *
 * Unlike the review reminder, there is no enabled-flag check here - the
 * digest is always-on (see CLAUDE.md's Round 10 section). Zero new words
 * this week still posts nothing, just re-arms for next Friday - the same
 * "must not be annoying" rule the review reminder follows.
 */
class DigestReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_FIRE_DIGEST, Intent.ACTION_BOOT_COMPLETED -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        handle(context, intent.action == ACTION_FIRE_DIGEST)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private suspend fun handle(context: Context, shouldNotify: Boolean) {
        val app = context.applicationContext as WordLookupApp

        if (shouldNotify) {
            val since = DigestWeek.startOfWeekMillis()
            val count = app.repository.observeRegister().first().count { it.createdAtMillis >= since }
            if (count > 0) postNotification(context, count)
        }

        // Re-arm regardless of whether this fire notified - both the weekly
        // alarm and a boot both need the next Friday scheduled, or the
        // digest notification silently stops.
        DigestReminderScheduler.schedule(context)
    }

    private fun postNotification(context: Context, count: Int) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Weekly digest", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Once a week, only when new words were saved."
            },
        )

        val openIntent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_DIGEST, true)
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val word = if (count == 1) "word" else "words"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("$count new $word this week")
            .setContentText("Tap to see them.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_FIRE_DIGEST = "com.harish.wordlookup.action.DIGEST_REMINDER_FIRE"
        private const val CHANNEL_ID = "digest_reminder"
        private const val NOTIFICATION_ID = 8200
        private const val REQUEST_CODE = 8201
    }
}
