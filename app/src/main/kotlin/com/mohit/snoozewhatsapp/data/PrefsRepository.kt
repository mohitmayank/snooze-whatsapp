package com.mohit.snoozewhatsapp.data

import android.content.Context
import android.content.SharedPreferences

class PrefsRepository private constructor(private val prefs: SharedPreferences) {

    // ── Snooze ──────────────────────────────────────────────────────────────

    var snoozeActive: Boolean
        get() = prefs.getBoolean(KEY_SNOOZE_ACTIVE, false)
        set(v) = prefs.edit().putBoolean(KEY_SNOOZE_ACTIVE, v).apply()

    var snoozeRestoreAt: Long
        get() = prefs.getLong(KEY_SNOOZE_RESTORE_AT, -1L)
        set(v) = prefs.edit().putLong(KEY_SNOOZE_RESTORE_AT, v).apply()

    var snoozeDailyStart: String
        get() = prefs.getString(KEY_SNOOZE_DAILY_START, "") ?: ""
        set(v) = prefs.edit().putString(KEY_SNOOZE_DAILY_START, v).apply()

    var snoozeDailyEnd: String
        get() = prefs.getString(KEY_SNOOZE_DAILY_END, "") ?: ""
        set(v) = prefs.edit().putString(KEY_SNOOZE_DAILY_END, v).apply()

    var snoozePendingAction: String
        get() = prefs.getString(KEY_SNOOZE_PENDING_ACTION, "") ?: ""
        set(v) = prefs.edit().putString(KEY_SNOOZE_PENDING_ACTION, v).apply()

    // ── Offline ──────────────────────────────────────────────────────────────

    var offlineActive: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE_ACTIVE, false)
        set(v) = prefs.edit().putBoolean(KEY_OFFLINE_ACTIVE, v).apply()

    var offlineRestoreAt: Long
        get() = prefs.getLong(KEY_OFFLINE_RESTORE_AT, -1L)
        set(v) = prefs.edit().putLong(KEY_OFFLINE_RESTORE_AT, v).apply()

    var offlineDailyStart: String
        get() = prefs.getString(KEY_OFFLINE_DAILY_START, "") ?: ""
        set(v) = prefs.edit().putString(KEY_OFFLINE_DAILY_START, v).apply()

    var offlineDailyEnd: String
        get() = prefs.getString(KEY_OFFLINE_DAILY_END, "") ?: ""
        set(v) = prefs.edit().putString(KEY_OFFLINE_DAILY_END, v).apply()

    // ── Onboarding ────────────────────────────────────────────────────────────

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(v) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, v).apply()

    // ── Settings ──────────────────────────────────────────────────────────────

    var showTestDurations: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TEST_DURATIONS, false)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_TEST_DURATIONS, v).apply()

    // ── Feature dispatch helpers ──────────────────────────────────────────────

    fun isActive(feature: Feature): Boolean = when (feature) {
        Feature.SNOOZE -> snoozeActive
        Feature.OFFLINE -> offlineActive
    }

    fun setActive(feature: Feature, active: Boolean) = when (feature) {
        Feature.SNOOZE -> { snoozeActive = active }
        Feature.OFFLINE -> { offlineActive = active }
    }

    fun getRestoreAt(feature: Feature): Long = when (feature) {
        Feature.SNOOZE -> snoozeRestoreAt
        Feature.OFFLINE -> offlineRestoreAt
    }

    fun setRestoreAt(feature: Feature, epochMs: Long) = when (feature) {
        Feature.SNOOZE -> { snoozeRestoreAt = epochMs }
        Feature.OFFLINE -> { offlineRestoreAt = epochMs }
    }

    fun getDailyStart(feature: Feature): String = when (feature) {
        Feature.SNOOZE -> snoozeDailyStart
        Feature.OFFLINE -> offlineDailyStart
    }

    fun setDailyStart(feature: Feature, hhmm: String) = when (feature) {
        Feature.SNOOZE -> { snoozeDailyStart = hhmm }
        Feature.OFFLINE -> { offlineDailyStart = hhmm }
    }

    fun getDailyEnd(feature: Feature): String = when (feature) {
        Feature.SNOOZE -> snoozeDailyEnd
        Feature.OFFLINE -> offlineDailyEnd
    }

    fun setDailyEnd(feature: Feature, hhmm: String) = when (feature) {
        Feature.SNOOZE -> { snoozeDailyEnd = hhmm }
        Feature.OFFLINE -> { offlineDailyEnd = hhmm }
    }

    fun clearRestoreAt(feature: Feature) = setRestoreAt(feature, -1L)

    fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val KEY_SNOOZE_ACTIVE = "snooze_active"
        const val KEY_SNOOZE_RESTORE_AT = "snooze_restore_at"
        const val KEY_SNOOZE_DAILY_START = "snooze_daily_start"
        const val KEY_SNOOZE_DAILY_END = "snooze_daily_end"
        const val KEY_SNOOZE_PENDING_ACTION = "snooze_pending_action"
        const val KEY_OFFLINE_ACTIVE = "offline_active"
        const val KEY_OFFLINE_RESTORE_AT = "offline_restore_at"
        const val KEY_OFFLINE_DAILY_START = "offline_daily_start"
        const val KEY_OFFLINE_DAILY_END = "offline_daily_end"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_SHOW_TEST_DURATIONS = "show_test_durations"

        const val PENDING_ENABLE = "ENABLE"
        const val PENDING_DISABLE = "DISABLE"

        @Volatile private var instance: PrefsRepository? = null

        fun get(context: Context): PrefsRepository =
            instance ?: synchronized(this) {
                instance ?: PrefsRepository(
                    context.applicationContext.getSharedPreferences("snooze_wa_prefs", Context.MODE_PRIVATE)
                ).also { instance = it }
            }
    }
}
