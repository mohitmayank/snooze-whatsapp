package com.mohit.snoozewhatsapp.tile

import android.content.SharedPreferences
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mohit.snoozewhatsapp.data.Feature
import com.mohit.snoozewhatsapp.data.FeatureController
import com.mohit.snoozewhatsapp.data.PrefsRepository
import com.mohit.snoozewhatsapp.ui.formatTimeRemaining

class OfflineTileService : TileService() {

    // Field reference prevents GC of the weakly-held listener
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PrefsRepository.KEY_OFFLINE_ACTIVE || key == PrefsRepository.KEY_OFFLINE_RESTORE_AT) {
            updateTile()
        }
    }

    override fun onStartListening() {
        PrefsRepository.get(this).registerOnSharedPreferenceChangeListener(prefsListener)
        updateTile()
    }

    override fun onStopListening() {
        PrefsRepository.get(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onClick() {
        val prefs = PrefsRepository.get(this)
        if (prefs.offlineActive) {
            FeatureController.deactivate(this, Feature.OFFLINE)
        } else {
            FeatureController.activate(this, Feature.OFFLINE)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val prefs = PrefsRepository.get(this)

        if (prefs.offlineActive) {
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val restoreAt = prefs.offlineRestoreAt
                tile.subtitle = if (restoreAt > 0) formatTimeRemaining(restoreAt) else "Active"
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = null
        }
        tile.updateTile()
    }
}
