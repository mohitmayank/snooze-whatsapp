package com.mohit.snoozewhatsapp.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.mohit.snoozewhatsapp.R
import com.mohit.snoozewhatsapp.data.Feature
import com.mohit.snoozewhatsapp.data.PrefsRepository
import com.mohit.snoozewhatsapp.ui.MainActivity
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.nio.ByteBuffer

class WhatsAppVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_START -> { start(); START_STICKY }
            ACTION_STOP -> { stop(); START_NOT_STICKY }
            else -> START_NOT_STICKY
        }
    }

    private fun start() {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val builder = Builder()
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .setSession("WA Offline")
            .setBlocking(false)

        try {
            builder.addAllowedApplication("com.whatsapp")
        } catch (e: Exception) {
            // WhatsApp not installed — stop gracefully
            stop()
            return
        }

        // Exclude our own app from the VPN tunnel
        builder.addDisallowedApplication(packageName)

        vpnInterface = builder.establish() ?: run {
            stop()
            return
        }

        PrefsRepository.get(this).offlineActive = true

        // Drop all packets entering the tunnel (= block WhatsApp network)
        scope.launch {
            val buf = ByteBuffer.allocate(32767)
            val stream = FileInputStream(vpnInterface!!.fileDescriptor)
            while (isActive) {
                try {
                    val len = stream.read(buf.array())
                    if (len > 0) buf.clear() // discard — WhatsApp gets no responses
                } catch (_: Exception) { break }
            }
        }
    }

    private fun stop() {
        scope.coroutineContext.cancelChildren()
        vpnInterface?.close()
        vpnInterface = null
        PrefsRepository.get(this).offlineActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.vpn_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, WhatsAppVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text))
            .setSmallIcon(R.drawable.ic_offline)
            .setContentIntent(openIntent)
            .addAction(
                Notification.Action.Builder(null, "Stop", stopIntent).build()
            )
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.mohit.snoozewhatsapp.VPN_START"
        const val ACTION_STOP = "com.mohit.snoozewhatsapp.VPN_STOP"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "wa_offline_channel"
    }
}
