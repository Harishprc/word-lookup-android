package com.harish.wordlookup.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Arms/disarms the once-a-week digest notification (round 10). Same inexact
 * `setAndAllowWhileIdle` choice as [ReviewReminderScheduler], for the same
 * reason (no SCHEDULE_EXACT_ALARM prompt for a feature whose whole point is
 * staying quiet). A separate scheduler/receiver pair rather than a third
 * branch on [ReviewReminderScheduler]/[ReviewReminderReceiver]: different
 * data source, different day-of-week math, different notification channel -
 * one class per concept, matching this repo's existing [ReviewReminderScheduler]
 * and [com.harish.wordlookup.data.speech.Speaker]-style separation.
 *
 * Unlike the review reminder, this alarm is always armed - there is no
 * Settings toggle for the digest (see CLAUDE.md's Round 10 section for why).
 */
object DigestReminderScheduler {

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextFireTime(), pendingIntent(context))
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DigestReminderReceiver::class.java).setAction(DigestReminderReceiver.ACTION_FIRE_DIGEST)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** The next Friday at [DIGEST_HOUR]:00 local time - today if it's Friday and that time hasn't passed, else the following Friday. */
    private fun nextFireTime(): Long {
        val now = Calendar.getInstance()
        val candidate = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, DIGEST_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (candidate.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY || !candidate.after(now)) {
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }
        return candidate.timeInMillis
    }

    private const val DIGEST_HOUR = 9
    private const val REQUEST_CODE = 8200
}
