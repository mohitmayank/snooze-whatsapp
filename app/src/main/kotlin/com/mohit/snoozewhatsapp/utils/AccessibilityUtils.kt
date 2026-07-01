package com.mohit.snoozewhatsapp.utils

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.mohit.snoozewhatsapp.accessibility.WhatsAppAccessibilityService

fun isAccessibilityEnabled(context: Context): Boolean {
    val serviceName = ComponentName(context, WhatsAppAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(":").any { it.equals(serviceName, ignoreCase = true) }
}
