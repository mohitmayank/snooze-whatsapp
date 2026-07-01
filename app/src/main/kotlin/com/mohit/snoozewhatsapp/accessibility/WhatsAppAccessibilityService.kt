package com.mohit.snoozewhatsapp.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
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
        Log.d(TAG, "onAccessibilityEvent: whatsapp window state changed, pending=$pending")
        if (pending.isEmpty()) return

        // WhatsApp's own internal navigation (Settings → Account → Privacy) fires window-state-changed
        // events too. Don't restart an already in-flight navigation on those — its polling loops pick up
        // the new screen on their own. Only start a fresh job if none is currently running.
        if (navigationJob?.isActive == true) return
        navigationJob = scope.launch {
            navigateToReadReceipts(pending == PrefsRepository.PENDING_ENABLE)
            prefs.snoozePendingAction = ""
        }
    }

    private suspend fun navigateToReadReceipts(enable: Boolean) {
        val timeoutMs = 5_000L

        Log.d(TAG, "navigateToReadReceipts: starting, enable=$enable")

        // Step through: More options → Settings → Privacy → Read receipts switch
        // "Privacy" is a top-level Settings row, a sibling of "Account" — not nested inside it.
        // Each step gets its own fresh timeout window — screen transitions take real time on-device.
        val steps = listOf("More options", "Settings", "Privacy")
        for (step in steps) {
            if (!clickNodeWithText(step, System.currentTimeMillis(), timeoutMs)) {
                Log.d(TAG, "navigateToReadReceipts: FAILED at step '$step'")
                notifyFailed()
                PrefsRepository.get(this).snoozeActive = !enable
                return
            }
            Log.d(TAG, "navigateToReadReceipts: clicked '$step'")
            delay(300)
        }

        // Find and click the Read receipts switch
        if (!clickReadReceiptsSwitch(enable, System.currentTimeMillis(), timeoutMs)) {
            Log.d(TAG, "navigateToReadReceipts: FAILED at Read receipts switch")
            notifyFailed()
            PrefsRepository.get(this).snoozeActive = !enable
            return
        }
        Log.d(TAG, "navigateToReadReceipts: clicked Read receipts switch, done")

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
            val node = findClickableNode(root, text)
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
        snoozeEnabled: Boolean,
        startTime: Long,
        timeoutMs: Long,
    ): Boolean {
        // Snooze ON means Read Receipts should be OFF, and vice versa — the switch's
        // desired checked state is the opposite of whether snooze is being enabled.
        val desiredChecked = !snoozeEnabled
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val root = rootInActiveWindow
            if (root == null) { delay(100); continue }
            val node = findSwitchNearText(root, "Read receipts")
            if (node != null) {
                val checked = node.isChecked
                // Only click if current state differs from desired. The Switch itself is often
                // not clickable — WhatsApp toggles it via a clickable row ancestor instead.
                if (checked != desiredChecked) {
                    val target = nearestClickableAncestor(node) ?: node
                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                node.recycle()
                return true
            }
            root.recycle()
            delay(150)
        }
        return false
    }

    /**
     * WhatsApp's menu/list rows are often a non-clickable label inside a clickable
     * ancestor container (e.g. a ListView row). Matching by text alone finds the label;
     * clicking it directly is a no-op, so climb up to the nearest clickable ancestor.
     *
     * findAccessibilityNodeInfosByText does a case-insensitive *contains* match against both
     * text and content-description, so unrelated elements can match (e.g. WhatsApp's account
     * switcher has content-desc "Button to switch or add an account", which contains "account").
     * Prefer nodes whose visible text is an exact match before falling back to loose matches.
     */
    private fun findClickableNode(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val matches = root.findAccessibilityNodeInfosByText(text)
        val ordered = matches.sortedByDescending { it.text?.toString().equals(text, ignoreCase = true) }
        for (match in ordered) {
            val clickable = nearestClickableAncestor(match)
            if (clickable != null) return clickable
        }
        return null
    }

    /** Returns `node` itself if clickable, else the nearest clickable ancestor, else null. */
    private fun nearestClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    /**
     * The label's Switch is not always a direct sibling — WhatsApp wraps the label in its own
     * layout, one level below the row container the switch also lives in. Climb multiple
     * ancestor levels searching each one's full subtree, and prefer the label with an exact
     * text match (surrounding description text can also contain the search string as a substring).
     */
    private fun findSwitchNearText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val labels = root.findAccessibilityNodeInfosByText(text)
        val ordered = labels.sortedByDescending { it.text?.toString().equals(text, ignoreCase = true) }
        for (label in ordered) {
            if (isSwitchOrCheckBox(label)) return label
            var ancestor: AccessibilityNodeInfo? = label.parent
            var depth = 0
            while (ancestor != null && depth < 4) {
                val switchNode = findDescendantSwitch(ancestor)
                if (switchNode != null) return switchNode
                ancestor = ancestor.parent
                depth++
            }
        }
        return null
    }

    private fun isSwitchOrCheckBox(node: AccessibilityNodeInfo): Boolean =
        node.className?.contains("Switch", ignoreCase = true) == true ||
            node.className?.contains("CheckBox", ignoreCase = true) == true

    private fun findDescendantSwitch(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (isSwitchOrCheckBox(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findDescendantSwitch(child)
            if (found != null) return found
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

    companion object {
        private const val TAG = "SnoozeA11y"
    }
}
