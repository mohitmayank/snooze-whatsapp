package com.mohit.snoozewhatsapp

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mohit.snoozewhatsapp.scheduler.StateCorrectWorker
import java.util.concurrent.TimeUnit

class SnoozeWhatsAppApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "state_correct",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StateCorrectWorker>(15, TimeUnit.MINUTES).build()
        )
    }
}
