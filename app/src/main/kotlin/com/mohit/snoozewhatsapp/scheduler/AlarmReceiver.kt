package com.mohit.snoozewhatsapp.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mohit.snoozewhatsapp.data.Feature
import com.mohit.snoozewhatsapp.data.FeatureController

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val featureName = intent.getStringExtra(ScheduleManager.EXTRA_FEATURE) ?: return
        val feature = runCatching { Feature.valueOf(featureName) }.getOrNull() ?: return
        val action = intent.getStringExtra(ScheduleManager.EXTRA_ACTION) ?: return

        when (action) {
            ScheduleManager.ALARM_ACTION_EXPIRE -> {
                FeatureController.deactivate(context, feature)
            }
            ScheduleManager.ALARM_ACTION_DAILY -> {
                val isStart = intent.getBooleanExtra(ScheduleManager.EXTRA_IS_START, true)
                val hour = intent.getIntExtra(ScheduleManager.EXTRA_DAILY_HOUR, 0)
                val minute = intent.getIntExtra(ScheduleManager.EXTRA_DAILY_MINUTE, 0)

                if (isStart) {
                    FeatureController.activate(context, feature)
                    // Self-reschedule for tomorrow
                    ScheduleManager.scheduleDailyStart(context, feature, hour, minute)
                } else {
                    FeatureController.deactivate(context, feature)
                    // Self-reschedule for tomorrow
                    ScheduleManager.scheduleDailyEnd(context, feature, hour, minute)
                }
            }
        }
    }
}
