package com.mohit.snoozewhatsapp.utils

import android.content.Context
import android.provider.Settings

fun isAccessibilityEnabled(context: Context): Boolean {
    val serviceName = "${context.packageName}/.accessibility.WhatsAppAccessibilityService"
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(":").any { it.equals(serviceName, ignoreCase = true) }
}
