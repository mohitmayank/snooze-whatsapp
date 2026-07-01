package com.mohit.snoozewhatsapp.data

import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import android.widget.Toast
import com.mohit.snoozewhatsapp.R
import com.mohit.snoozewhatsapp.tile.OfflineTileService
import com.mohit.snoozewhatsapp.tile.SnoozeTileService
import com.mohit.snoozewhatsapp.utils.VpnConflictChecker
import com.mohit.snoozewhatsapp.vpn.WhatsAppVpnService

object FeatureController {

    fun activate(context: Context, feature: Feature) {
        val prefs = PrefsRepository.get(context)
        prefs.setActive(feature, true)
        when (feature) {
            Feature.SNOOZE -> triggerSnoozeToggle(context, enable = true)
            Feature.OFFLINE -> startVpn(context)
        }
        requestTileUpdate(context, feature)
    }

    fun deactivate(context: Context, feature: Feature) {
        val prefs = PrefsRepository.get(context)
        prefs.setActive(feature, false)
        prefs.clearRestoreAt(feature)
        when (feature) {
            Feature.SNOOZE -> triggerSnoozeToggle(context, enable = false)
            Feature.OFFLINE -> stopVpn(context)
        }
        requestTileUpdate(context, feature)
    }

    private fun triggerSnoozeToggle(context: Context, enable: Boolean) {
        val prefs = PrefsRepository.get(context)
        prefs.snoozePendingAction = if (enable) PrefsRepository.PENDING_ENABLE else PrefsRepository.PENDING_DISABLE
        // Launch WhatsApp; AccessibilityService picks up the pending action on window focus
        val waIntent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
        if (waIntent != null) {
            waIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(waIntent)
        }
    }

    private fun startVpn(context: Context) {
        if (VpnConflictChecker.isVpnActive(context)) {
            Toast.makeText(context, context.getString(R.string.vpn_conflict_error), Toast.LENGTH_LONG).show()
            // Revert the prefs change — VPN did not start
            PrefsRepository.get(context).offlineActive = false
            return
        }
        val intent = Intent(context, WhatsAppVpnService::class.java).apply {
            action = WhatsAppVpnService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    private fun stopVpn(context: Context) {
        val intent = Intent(context, WhatsAppVpnService::class.java).apply {
            action = WhatsAppVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    private fun requestTileUpdate(context: Context, feature: Feature) {
        when (feature) {
            Feature.SNOOZE -> TileService.requestListeningState(
                context, android.content.ComponentName(context, SnoozeTileService::class.java)
            )
            Feature.OFFLINE -> TileService.requestListeningState(
                context, android.content.ComponentName(context, OfflineTileService::class.java)
            )
        }
    }
}
