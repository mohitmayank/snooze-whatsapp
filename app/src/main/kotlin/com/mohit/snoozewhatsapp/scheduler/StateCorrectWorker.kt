package com.mohit.snoozewhatsapp.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mohit.snoozewhatsapp.data.Feature
import com.mohit.snoozewhatsapp.data.FeatureController
import com.mohit.snoozewhatsapp.data.PrefsRepository

class StateCorrectWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = PrefsRepository.get(applicationContext)
        val now = System.currentTimeMillis()
        for (feature in Feature.entries) {
            val restoreAt = prefs.getRestoreAt(feature)
            if (restoreAt != -1L && now >= restoreAt && prefs.isActive(feature)) {
                // Duration expired but exact alarm misfired (doze) — correct now
                FeatureController.deactivate(applicationContext, feature)
            }
        }
        return Result.success()
    }
}
