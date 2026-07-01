package com.mohit.snoozewhatsapp.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mohit.snoozewhatsapp.data.Feature
import com.mohit.snoozewhatsapp.data.PrefsRepository
import java.util.*

object ScheduleManager {

    // Alarm request codes (unique per feature × action)
    private const val REQ_SNOOZE_EXPIRE = 100
    private const val REQ_SNOOZE_DAILY_START = 101
    private const val REQ_SNOOZE_DAILY_END = 102
    private const val REQ_OFFLINE_EXPIRE = 200
    private const val REQ_OFFLINE_DAILY_START = 201
    private const val REQ_OFFLINE_DAILY_END = 202

    const val EXTRA_FEATURE = "feature"
    const val EXTRA_ACTION = "alarm_action"
    const val EXTRA_DAILY_HOUR = "daily_hour"
    const val EXTRA_DAILY_MINUTE = "daily_minute"
    const val EXTRA_IS_START = "is_start"

    const val ALARM_ACTION_EXPIRE = "expire"
    const val ALARM_ACTION_DAILY = "daily"

    fun scheduleDurationOff(context: Context, feature: Feature, durationMs: Long) {
        val triggerAt = System.currentTimeMillis() + durationMs
        PrefsRepository.get(context).setRestoreAt(feature, triggerAt)
        scheduleExact(context, triggerAt, buildExpirePendingIntent(context, feature))
    }

    fun scheduleDailyStart(context: Context, feature: Feature, hour: Int, minute: Int) {
        val triggerAt = nextOccurrenceOf(hour, minute)
        scheduleExact(context, triggerAt, buildDailyPendingIntent(context, feature, hour, minute, isStart = true))
    }

    fun scheduleDailyEnd(context: Context, feature: Feature, hour: Int, minute: Int) {
        val triggerAt = nextOccurrenceOf(hour, minute)
        scheduleExact(context, triggerAt, buildDailyPendingIntent(context, feature, hour, minute, isStart = false))
    }

    fun cancelDuration(context: Context, feature: Feature) {
        alarmManager(context).cancel(buildExpirePendingIntent(context, feature))
        PrefsRepository.get(context).clearRestoreAt(feature)
    }

    fun cancelDaily(context: Context, feature: Feature) {
        val pi = buildDailyPendingIntent(context, feature, 0, 0, isStart = true)
        val pi2 = buildDailyPendingIntent(context, feature, 0, 0, isStart = false)
        alarmManager(context).apply { cancel(pi); cancel(pi2) }
        PrefsRepository.get(context).apply { setDailyStart(feature, ""); setDailyEnd(feature, "") }
    }

    /** Returns the next epoch ms for the given HH:mm (today if not yet passed, tomorrow otherwise). */
    fun nextOccurrenceOf(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun scheduleExact(context: Context, triggerAt: Long, pi: PendingIntent) {
        val am = alarmManager(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // Fallback: ±10 min window
            am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60 * 1000L, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun alarmManager(context: Context) =
        context.getSystemService(AlarmManager::class.java)

    private fun buildExpirePendingIntent(context: Context, feature: Feature): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.mohit.snoozewhatsapp.ACTION_ALARM"
            putExtra(EXTRA_FEATURE, feature.name)
            putExtra(EXTRA_ACTION, ALARM_ACTION_EXPIRE)
        }
        val reqCode = if (feature == Feature.SNOOZE) REQ_SNOOZE_EXPIRE else REQ_OFFLINE_EXPIRE
        return PendingIntent.getBroadcast(context, reqCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun buildDailyPendingIntent(
        context: Context,
        feature: Feature,
        hour: Int,
        minute: Int,
        isStart: Boolean,
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.mohit.snoozewhatsapp.ACTION_ALARM"
            putExtra(EXTRA_FEATURE, feature.name)
            putExtra(EXTRA_ACTION, ALARM_ACTION_DAILY)
            putExtra(EXTRA_DAILY_HOUR, hour)
            putExtra(EXTRA_DAILY_MINUTE, minute)
            putExtra(EXTRA_IS_START, isStart)
        }
        val reqCode = when {
            feature == Feature.SNOOZE && isStart -> REQ_SNOOZE_DAILY_START
            feature == Feature.SNOOZE && !isStart -> REQ_SNOOZE_DAILY_END
            feature == Feature.OFFLINE && isStart -> REQ_OFFLINE_DAILY_START
            else -> REQ_OFFLINE_DAILY_END
        }
        return PendingIntent.getBroadcast(context, reqCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
