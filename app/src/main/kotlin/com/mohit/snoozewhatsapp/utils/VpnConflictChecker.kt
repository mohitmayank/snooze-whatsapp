package com.mohit.snoozewhatsapp.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object VpnConflictChecker {
    fun isVpnActive(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }
}
