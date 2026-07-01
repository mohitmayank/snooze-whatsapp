package com.mohit.snoozewhatsapp.ui

fun formatTimeRemaining(restoreAtMs: Long): String {
    val remaining = restoreAtMs - System.currentTimeMillis()
    if (remaining <= 0) return "Active"
    val minutes = remaining / 60_000
    return when {
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0L -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}
