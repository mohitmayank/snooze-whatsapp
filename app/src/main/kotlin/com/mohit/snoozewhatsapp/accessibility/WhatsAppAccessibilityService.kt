package com.mohit.snoozewhatsapp.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.mohit.snoozewhatsapp.R
import com.mohit.snoozewhatsapp.data.PrefsRepository
import kotlinx.coroutines.*

class WhatsAppAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var navigationJob: Job? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName != "com.whatsapp") return

        val prefs = PrefsRepository.get(this)
        val pending = prefs.snoozePendingAction
        if (pending.isEmpty()) return

        // Cancel any previous navigation attempt and start fresh
        navigationJob?.cancel()
        navigationJob = scope.launch {
            navigateToReadReceipts(pending == PrefsRepository.PENDING_ENABLE)
            prefs.snoozePendingAction = ""
        }
    }

    private suspend fun navigateToReadReceipts(enable: Boolean) {
        val timeoutMs = 5_000L
        val startTime = System.currentTimeMillis()

        fun elapsed() = System.currentTimeMillis() - startTime
        fun timedOut() = elapsed() > timeoutMs

        // Step through: Settings → Account → Privacy → Read receipts switch
        val steps = listOf("Settings", "Account", "Privacy")
        for (step in steps) {
            if (!clickNodeWithText(step, startTime, timeoutMs)) {
                notifyFailed()
                return
            }
            delay(300)
        }

        // Find and click the Read receipts switch
        if (!clickReadReceiptsSwitch(enable, startTime, timeoutMs)) {
            notifyFailed()
            return
        }

        delay(500)
        // Navigate back to previous app
        performGlobalAction(GLOBAL_ACTION_BACK)
        delay(200)
        performGlobalAction(GLOBAL_ACTION_BACK)
        delay(200)
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)

        PrefsRepository.get(this).snoozeActive = enable
    }

    private suspend fun clickNodeWithText(
        text: String,
        startTime: Long,
        timeoutMs: Long,
    ): Boolean {
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val root = rootInActiveWindow
            if (root == null) { delay(100); continue }
            val node = findNodeByText(root, text)
            if (node != null) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node.recycle()
                return true
            }
            root.recycle()
            delay(150)
        }
        return false
    }

    private suspend fun clickReadReceiptsSwitch(
        enable: Boolean,
        startTime: Long,
        timeoutMs: Long,
    ): Boolean {
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val root = rootInActiveWindow
            if (root == null) { delay(100); continue }
            val node = findSwitchNearText(root, "Read receipts")
            if (node != null) {
                val checked = node.isChecked
                // Only click if current state differs from desired
                if (checked != enable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                node.recycle()
                return true
            }
            root.recycle()
            delay(150)
        }
        return false
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes.firstOrNull { it.isClickable || it.isEnabled }
    }

    private fun findSwitchNearText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        // Find the label, then look for a sibling/nearby switch
        val labels = root.findAccessibilityNodeInfosByText(text)
        for (label in labels) {
            val parent = label.parent ?: continue
            for (i in 0 until parent.childCount) {
                val child = parent.getChild(i) ?: continue
                if (child.className?.contains("Switch", ignoreCase = true) == true ||
                    child.className?.contains("CheckBox", ignoreCase = true) == true
                ) {
                    return child
                }
                child.recycle()
            }
            // Also check the label itself if it's a switch
            if (label.className?.contains("Switch", ignoreCase = true) == true) {
                return label
            }
            label.recycle()
            parent.recycle()
        }
        return null
    }

    private fun notifyFailed() {
        val channelId = "wa_snooze_alerts"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, getString(R.string.notification_channel_snooze_name), NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.notify(1002, Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.snooze_failed_notification_title))
            .setContentText(getString(R.string.snooze_failed_notification_text))
            .setSmallIcon(R.drawable.ic_snooze)
            .setAutoCancel(true)
            .build()
        )
    }

    override fun onInterrupt() {
        navigationJob?.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
