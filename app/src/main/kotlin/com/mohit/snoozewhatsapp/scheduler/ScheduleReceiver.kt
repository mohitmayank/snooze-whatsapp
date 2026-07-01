package com.mohit.snoozewhatsapp.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mohit.snoozewhatsapp.data.Feature
import com.mohit.snoozewhatsapp.data.PrefsRepository

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = PrefsRepository.get(context)
        val now = System.currentTimeMillis()
        for (feature in Feature.entries) {
            // Re-register duration alarm if still in the future
            val restoreAt = prefs.getRestoreAt(feature)
            if (restoreAt > now) {
                ScheduleManager.scheduleDurationOff(context, feature, restoreAt - now)
            } else if (restoreAt != -1L && restoreAt <= now) {
                // Timer expired during reboot — deactivate immediately
                prefs.setActive(feature, false)
                prefs.clearRestoreAt(feature)
            }

            // Re-register daily alarms
            val dailyStart = prefs.getDailyStart(feature)
            if (dailyStart.isNotEmpty()) {
                val (h, m) = dailyStart.split(":").map { it.toInt() }
                ScheduleManager.scheduleDailyStart(context, feature, h, m)
            }
            val dailyEnd = prefs.getDailyEnd(feature)
            if (dailyEnd.isNotEmpty()) {
                val (h, m) = dailyEnd.split(":").map { it.toInt() }
                ScheduleManager.scheduleDailyEnd(context, feature, h, m)
            }
        }
    }
}
