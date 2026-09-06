package com.harish.wordlookup.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Arms/disarms the once-a-day, silent review reminder (round 8). Everything
 * about this scheduler is chosen to keep the notification genuinely
 * low-key, per the standing "must not be annoying" instruction:
 *
 * - `setAndAllowWhileIdle`, not `setExactAndAllowWhileIdle` - an *inexact*
 *   alarm needs no SCHEDULE_EXACT_ALARM permission, which on API 31+ is a
 *   user-facing prompt out of proportion for a feature whose whole point is
 *   staying quiet. A few minutes of drift on a once-a-day reminder is a
 *   fair trade.
 * - AlarmManager over re-adding WorkManager: round 5 removed
 *   `work-runtime-ktx` deliberately (see CLAUDE.md's "Sync removal"), and
 *   bringing it back would drag `lifecycle-livedata` in transitively and
 *   merge WorkManager's own components into the manifest - a much larger
 *   parity delta than one receiver, for a job with no retry/backoff needs.
 *
 * Whether the alarm actually posts anything is decided in
 * [ReviewReminderReceiver] at fire time (only if something is due), not
 * here - this class only owns *when* the receiver next runs.
 */
object ReviewReminderScheduler {

    fun schedule(context: Context, hour: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val next = nextFireTime(hour)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent(context))
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReviewReminderReceiver::class.java).setAction(ReviewReminderReceiver.ACTION_FIRE)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** The next occurrence of [hour]:00 local time - today if it hasn't passed yet, tomorrow if it has. */
    private fun nextFireTime(hour: Int): Long {
        val now = Calendar.getInstance()
        val candidate = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!candidate.after(now)) {
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }
        return candidate.timeInMillis
    }

    private const val REQUEST_CODE = 8100
}
