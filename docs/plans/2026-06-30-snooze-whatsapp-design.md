# Snooze WhatsApp — Technical Design Spec
**Date**: 2026-06-30  
**Status**: Rev 3 (post-review fixes)  
**Target**: Kotlin native Android, compileSdk 34, minSdk 26 (Android 8), tested on API 33/34

---

## 1. Problem & Goal

WhatsApp's read receipts and network access cannot be quickly toggled from the Android system UI. This app provides quick-access control — two Quick Settings tiles, a simple main screen, and a scheduler — to temporarily disable:

1. **Read receipts** (Snooze): others can't see if you've read their messages
2. **WhatsApp network access** (Offline): WhatsApp cannot send/receive any data

Both features support immediate toggle and scheduled activation (duration-based one-shot, or daily repeating time-range). No root required.

---

## 2. Stack & Dependencies

| Concern | Choice | Reason |
|---------|--------|--------|
| Language | Kotlin | All required APIs are native Android |
| Min SDK | 26 (Android 8) | `TileService` stable API 24; `VpnService` API 14 |
| Target SDK | 34 (Android 14) | User's device |
| UI | Jetpack Compose + Material 3 | Minimal boilerplate for simple single-screen UI |
| State | `SharedPreferences` | Simple key-value; no DB needed |
| Scheduling | `AlarmManager.setExactAndAllowWhileIdle` + self-reschedule | Exact alarms; `setRepeating` is inexact since API 19 |
| Doze fallback | `WorkManager` `PeriodicWorkRequest` (15m min) | Corrects state drift if exact alarm misfires |
| Build | Gradle Kotlin DSL `.kts` | Standard |

**External dependencies**:
```kotlin
implementation("androidx.core:core-ktx:1.13.1")
implementation("androidx.compose.ui:ui:1.6.8")
implementation("androidx.compose.material3:material3:1.2.1")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
implementation("androidx.work:work-runtime-ktx:2.9.0")
```

---

## 3. Architecture

### Package Layout

```
com.mohit.snoozewhatsapp
├── ui/
│   ├── MainActivity.kt              # Compose entry point
│   └── PermissionSetupScreen.kt     # First-run permission onboarding flow
├── tile/
│   ├── SnoozeTileService.kt         # Quick Settings tile — read receipts
│   └── OfflineTileService.kt        # Quick Settings tile — network block
├── accessibility/
│   └── WhatsAppAccessibilityService.kt  # Navigates WA UI; toggles read receipt setting
├── vpn/
│   └── WhatsAppVpnService.kt        # Local VPN tunnel; routes+drops WA-only traffic
├── scheduler/
│   ├── ScheduleManager.kt           # AlarmManager wrapper
│   ├── ScheduleReceiver.kt          # BroadcastReceiver for BOOT_COMPLETED only
│   ├── AlarmReceiver.kt             # exported=false; receives custom ACTION_ALARM intents
│   └── StateCorrectWorker.kt        # WorkManager Worker; corrects state drift every 15m
├── data/
│   └── PrefsRepository.kt           # Single source of truth; all persisted state
└── utils/
    └── VpnConflictChecker.kt        # Checks ConnectivityManager for active VPN
```

### Data Model (SharedPreferences keys)

```kotlin
// Per-feature state (replace SNOOZE with OFFLINE for other feature)
"snooze_active": Boolean
"snooze_restore_at": Long          // epoch ms; -1 = no duration timer running
"snooze_daily_start": String       // "HH:mm"; "" = not set
"snooze_daily_end": String         // "HH:mm"; "" = not set
"snooze_pending_action": String    // "ENABLE" | "DISABLE" | "" — read by AccessibilityService

// First-run flags
"onboarding_done": Boolean
```

---

## 4. Feature: Snooze (Read Receipts)

### Mechanism
`AccessibilityService` monitors WhatsApp window events and, when it detects WhatsApp in the foreground with a pending action, traverses the menu tree to toggle the Read Receipts setting.

**Why not broadcast directly to AccessibilityService**: Android does not allow external apps to bind to or directly broadcast into `AccessibilityService`. Communication is via `SharedPreferences`.

### Trigger Flow
1. User taps toggle (app or tile) → `PrefsRepository.setSnoozeActive(true)` + `setPendingAction(Feature.SNOOZE, "ENABLE")`
2. App launches WhatsApp via `packageManager.getLaunchIntentForPackage("com.whatsapp")` with `addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)`
3. `WhatsAppAccessibilityService.onAccessibilityEvent()` receives `TYPE_WINDOW_STATE_CHANGED`
4. Service checks `PrefsRepository.getPendingAction(Feature.SNOOZE)` — if "ENABLE" or "DISABLE":
   - Navigate: find node with `text="Settings"` → click → wait → `text="Account"` → click → `text="Privacy"` → click → `text="Read receipts"` switch node → click
   - On success: clear `pendingAction`, update `snooze_active`, send broadcast `com.mohit.snoozewhatsapp.ACTION_SNOOZE_DONE`
   - On failure (node not found after 5s): set `pendingAction=""`, notify via `NotificationManager` "WhatsApp UI changed — re-open settings manually"
5. Service presses Back until WhatsApp is minimized (or sends HOME intent)
6. Tiles and UI observe `PrefsRepository` via listener and refresh

### AccessibilityService Config (`res/xml/accessibility_service_config.xml`)
```xml
<accessibility-service
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFlags="flagDefault"
    android:canRetrieveWindowContent="true"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="100"
    android:packageNames="com.whatsapp"
    android:settingsActivity=".ui.MainActivity" />
```

### Error Handling
- WhatsApp not installed → disable snooze feature, tile = `STATE_UNAVAILABLE`
- Node not found in 5s → notify user, clear pending action
- Accessibility permission revoked → tile = `STATE_UNAVAILABLE`; in-app banner with deep-link to `Settings.ACTION_ACCESSIBILITY_SETTINGS`

---

## 5. Feature: Offline (Network Block)

### Mechanism
`VpnService` routes **only WhatsApp's traffic** through a local TUN interface using `addAllowedApplication("com.whatsapp")`. Everything entering the tunnel is dropped. All other apps' traffic bypasses the VPN entirely.

**Why this approach**: You cannot read originating UID from raw IP packets on a TUN fd without root (`Os.getsockopt(SO_PEERCRED)` is for Unix sockets only). Instead, `Builder.addAllowedApplication` constrains which app's traffic enters the tunnel — so dropping all tunnel traffic is equivalent to blocking only WhatsApp. No per-packet UID inspection needed.

### VPN Service Flow
1. User action → `VpnConflictChecker.check()` → if active VPN detected → show `AlertDialog` and abort
2. `VpnService.prepare(context)` → if non-null Intent returned → launch it (Android VPN consent dialog, first time only)
3. On consent: start `WhatsAppVpnService` via `Intent(ACTION_START)`
4. `WhatsAppVpnService.onStartCommand()` builds the interface:
   ```kotlin
   Builder()
       .addAddress("10.0.0.2", 32)
       .addRoute("0.0.0.0", 0)                        // capture all traffic entering tunnel
       .addAllowedApplication("com.whatsapp")          // ONLY WhatsApp enters tunnel
       .setSession("WA Offline")
       .setBlocking(false)
       .establish()
   ```
5. Open the TUN fd in a coroutine; read loop discards all packets (WhatsApp gets no responses → appears offline)
6. Show persistent foreground notification: "WhatsApp offline — tap to stop"
7. Stop: `stopSelf()` → `establish()` returns null → VPN tears down automatically

### VPN Conflict Detection
```kotlin
val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
return caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
```

### Manifest entries
```xml
<service android:name=".vpn.WhatsAppVpnService"
    android:permission="android.permission.BIND_VPN_SERVICE"
    android:foregroundServiceType="specialUse">
    <intent-filter>
        <action android:name="android.net.VpnService" />
    </intent-filter>
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="WhatsApp network blocking" />
</service>
```

---

## 6. Quick Settings Tiles

Two `TileService` subclasses.

### Tile States

| State | Condition | Appearance |
|-------|-----------|------------|
| `STATE_ACTIVE` | Feature on | Colored icon; subtitle = time remaining or "Active" |
| `STATE_INACTIVE` | Feature off | Grey icon |
| `STATE_UNAVAILABLE` | Permission not granted | Greyed; subtitle "Setup required" |

### SharedPreferences Listener (GC-safe)

**Important**: `SharedPreferences.OnSharedPreferenceChangeListener` is weakly held; store as a field:

```kotlin
class SnoozeTileService : TileService() {
    // Field reference prevents GC
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PrefsRepository.KEY_SNOOZE_ACTIVE) updateTile()
    }

    override fun onStartListening() {
        PrefsRepository.get(this).registerOnSharedPreferenceChangeListener(prefsListener)
        updateTile()
    }

    override fun onStopListening() {
        PrefsRepository.get(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
    }
}
```

- **Tap** → toggle feature; if duration configured in app, start that duration
- **Long-press** → Android opens `MainActivity` (via `android:settingsActivity`)

---

## 7. Scheduling

### Why not `setRepeating` for daily alarms
`AlarmManager.setRepeating()` has been inexact since API 19 (Android 4.4) — Android batches it with other alarms and can fire ±30–60 minutes off. For daily schedules, use `setExactAndAllowWhileIdle` and self-reschedule.

### Duration Mode (one-shot auto-restore)

```kotlin
fun scheduleDurationOff(feature: Feature, durationMs: Long) {
    val triggerAt = System.currentTimeMillis() + durationMs
    PrefsRepository.setRestoreAt(feature, triggerAt)
    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        triggerAt,
        pendingIntent(feature, ACTION_DURATION_EXPIRE)
    )
}
```

`AlarmReceiver` receives `ACTION_DURATION_EXPIRE` → deactivates feature → updates `PrefsRepository` → tiles refresh.

### Daily Time-Range Mode (self-rescheduling)

```kotlin
fun scheduleDailyStart(feature: Feature, hour: Int, minute: Int) {
    val triggerAt = nextOccurrenceOf(hour, minute)  // today or tomorrow
    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        triggerAt,
        pendingIntent(feature, ACTION_DAILY_START)
    )
}
```

`ScheduleReceiver.onReceive(ACTION_DAILY_START)`:
1. Activate feature
2. Reschedule next day: `scheduleDailyStart(feature, hour, minute)` (adds 24h)

Same pattern for `ACTION_DAILY_END`.

### Exact Alarm Permission (API 33+)

`SCHEDULE_EXACT_ALARM` on API 33+ is a runtime "Special App Access" permission (Settings > Apps > Special app access > Alarms & reminders). It is NOT auto-granted.

`USE_EXACT_ALARM` is auto-granted but restricted to calendar/alarm-clock apps; using it for a general app risks Play Store rejection.

**Approach**:
```kotlin
if (alarmManager.canScheduleExactAlarms()) {
    alarmManager.setExactAndAllowWhileIdle(...)
} else {
    // Fall back to setWindow (±10min window) — acceptable for daily schedule
    alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60 * 1000L, pi)
    // Also show in-app prompt: "Grant exact alarm permission for precise scheduling"
    // Deep-link: startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
}
```

### Boot Receiver

```kotlin
// ScheduleReceiver also handles BOOT_COMPLETED
if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
    // Re-register all active alarms from PrefsRepository
    for (feature in Feature.entries) {
        val restoreAt = PrefsRepository.getRestoreAt(feature)
        if (restoreAt > System.currentTimeMillis()) scheduleDurationOff(feature, restoreAt - now)
        val dailyStart = PrefsRepository.getDailyStart(feature)
        if (dailyStart.isNotEmpty()) scheduleDailyStart(feature, ...)
        val dailyEnd = PrefsRepository.getDailyEnd(feature)
        if (dailyEnd.isNotEmpty()) scheduleDailyEnd(feature, ...)
    }
}
```

### Broadcast Security

Split into two receivers to avoid the boot-receiver blocking bug: a top-level `android:permission` on `<receiver>` applies to ALL senders including the Android system, which does not hold custom permissions — so `BOOT_COMPLETED` would be silently dropped.

```xml
<permission android:name="com.mohit.snoozewhatsapp.ALARM_PERMISSION"
    android:protectionLevel="signature" />

<!-- BOOT_COMPLETED: system sender; no custom permission guard needed -->
<receiver android:name=".scheduler.ScheduleReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>

<!-- Custom alarm actions: exported=false; AlarmManager delivers as our UID -->
<receiver android:name=".scheduler.AlarmReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.mohit.snoozewhatsapp.ACTION_ALARM" />
    </intent-filter>
</receiver>
```

`PendingIntent` for AlarmManager uses `FLAG_IMMUTABLE`. Since `AlarmReceiver` is `exported=false`, only our own app can target it — no signature permission needed on the custom alarm receiver itself.

### WorkManager Doze Fallback

`StateCorrectWorker` runs every 15 minutes (minimum WorkManager interval) and corrects drift:

```kotlin
class StateCorrectWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = PrefsRepository.get(applicationContext)
        for (feature in Feature.entries) {
            val restoreAt = prefs.getRestoreAt(feature)
            if (restoreAt != -1L && System.currentTimeMillis() >= restoreAt) {
                // Duration timer expired but alarm never fired (doze); deactivate now
                FeatureController.deactivate(applicationContext, feature)
            }
        }
        return Result.success()
    }
}

// Enqueue once at app start (idempotent via unique work name)
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "state_correct",
    ExistingPeriodicWorkPolicy.KEEP,
    PeriodicWorkRequestBuilder<StateCorrectWorker>(15, TimeUnit.MINUTES).build()
)
```

Added to package layout: `scheduler/StateCorrectWorker.kt`.

---

## 8. First-Run Permission Onboarding

Three permissions require user action (none are auto-granted):
1. **Accessibility Service** — `Settings.ACTION_ACCESSIBILITY_SETTINGS`
2. **VPN consent** — `VpnService.prepare()` dialog (system-shown, first time only)
3. **Exact Alarm** — `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` (API 33+)

`PermissionSetupScreen.kt` (Compose):
- Shown on first launch (`onboarding_done == false`) or when any permission is revoked
- Three steps displayed as a checklist; each row has a "Grant" button that deep-links to the relevant settings screen
- App polls permission state in `onResume` via `checkPermissions()` and updates checklist
- "Done" button enabled only when Accessibility + VPN consent are granted (Exact Alarm degrades gracefully to `setWindow`)

---

## 9. Main App UI (Jetpack Compose)

Single `MainActivity`; shows `PermissionSetupScreen` if onboarding not done, else shows main screen.

```
SnoozeWhatsAppTheme
└── Column
    ├── PermissionsBanner (compact; shown if any permission missing after onboarding)
    ├── FeatureCard(Feature.SNOOZE)
    │   ├── Row: title "WA Snooze" + Switch
    │   ├── StatusText (e.g., "Active — restores in 47m")
    │   └── ScheduleSection
    │       ├── DurationChips [30m][1h][2h][4h][Custom]
    │       └── DailyRangePicker (TimePicker start → end)
    └── FeatureCard(Feature.OFFLINE)
        └── (same structure)
```

`FeatureCard` is one composable parameterized by `Feature` enum.

---

## 10. Permissions & Manifest Summary

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<!-- Required on API 33+ for VPN foreground notification and accessibility failure notification -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Custom signature permission for alarm spoofing protection -->
<permission android:name="com.mohit.snoozewhatsapp.ALARM_PERMISSION"
    android:protectionLevel="signature" />
<uses-permission android:name="com.mohit.snoozewhatsapp.ALARM_PERMISSION" />
```

---

## 11. Key Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| WhatsApp UI changes break accessibility navigation | Match by `text` + `contentDescription`; 5s timeout → user notification |
| Android battery optimization kills AlarmManager | `setExactAndAllowWhileIdle`; WorkManager fallback; prompt user to add to battery whitelist |
| VPN conflict with work/other VPN | Pre-check `TRANSPORT_VPN`; show error dialog, abort |
| Accessibility permission revoked | Tile → `STATE_UNAVAILABLE`; in-app banner |
| WhatsApp not installed | Disable both features; show empty state |
| VPN service killed (memory pressure) | Notification disappears; tile auto-updates; user re-activates |
| `SCHEDULE_EXACT_ALARM` not granted | Fall back to `setWindow` (±10 min); in-app prompt to grant |

---

## 12. Android Dev Setup Steps

JDK 21 already present.

```bash
# 1. Android Studio
sudo snap install android-studio --classic

# 2. Open Android Studio → SDK Manager → install:
#    - Android SDK Platform 34
#    - Android SDK Build-Tools 34.0.0
#    - Android SDK Platform-Tools (includes adb)

# 3. Add ADB to PATH
echo 'export PATH="$HOME/Android/Sdk/platform-tools:$PATH"' >> ~/.zshrc && source ~/.zshrc

# 4. Connect device with USB Debugging enabled
adb devices

# 5. Build & install
./gradlew assembleDebug
./gradlew installDebug
adb logcat -s SnoozeWA
```

---

## 13. Test Plan

### Acceptance Criteria per Feature

| # | Scenario | Expected Result |
|---|----------|-----------------|
| 1 | Toggle Snooze ON (accessibility granted, WA installed) | WhatsApp opens, navigates to Privacy, flips Read Receipts; returns to previous app; tile goes active |
| 2 | Toggle Snooze ON (accessibility NOT granted) | Shows "Setup required" banner; tile = unavailable |
| 3 | Toggle Offline ON (no other VPN active) | WhatsApp shows no internet; other apps unaffected; VPN key icon in status bar |
| 4 | Toggle Offline ON (another VPN active) | Error dialog shown; VPN does not start |
| 5 | Set 30m duration, activate Snooze | Auto-deactivates after 30 min; tile returns to inactive |
| 6 | Set daily 22:00–08:00 for Offline | Activates at 10pm; deactivates at 8am; persists across app restarts |
| 7 | Reboot device with daily schedule set | Alarms re-registered on boot; schedule resumes correctly |
| 8 | Tap Quick Settings tile (Snooze, inactive) | Toggles feature on |
| 9 | Long-press Quick Settings tile | Opens MainActivity |
| 10 | `SCHEDULE_EXACT_ALARM` not granted | Falls back to `setWindow`; in-app prompt shown |

### Unit Test Targets
- `ScheduleManager`: alarm scheduling, self-reschedule logic, boot re-registration
- `PrefsRepository`: state transitions, key correctness
- `VpnConflictChecker`: returns true/false based on mocked `NetworkCapabilities`

### Instrumented Test Targets
- `WhatsAppVpnService`: verify VPN establishes; verify WhatsApp traffic is blocked; verify other app traffic unaffected (use `OkHttp` pings in test)
- `ScheduleReceiver`: send `ACTION_BOOT_COMPLETED`; verify alarms re-registered

### Accessibility Service Testing (UIAutomator)
`WhatsAppAccessibilityService` is the highest-risk component (breaks on WhatsApp UI updates). Test strategy:

```kotlin
// UIAutomator2 test — runs on device with WhatsApp installed
@Test fun snoozeTriggerNavigatesToReadReceiptsAndToggles() {
    // 1. Enable accessibility service in test setUp via Settings
    // 2. Set pending action via PrefsRepository
    PrefsRepository.get(context).setPendingAction(Feature.SNOOZE, "ENABLE")
    // 3. Launch WhatsApp
    context.startActivity(getLaunchIntent("com.whatsapp"))
    // 4. Wait for navigation to complete (observe prefs change)
    val changed = awaitPrefsChange(PrefsRepository.KEY_SNOOZE_ACTIVE, timeoutMs = 10_000)
    assertTrue(changed)
    // 5. Verify WA privacy settings reflect the toggle (query WA prefs via a second accessibility pass)
}
```

Also add a **node-map snapshot test**: run a separate instrumented test that logs the full WhatsApp Settings accessibility tree to logcat, so you can quickly detect UI changes and update node selectors.

### Manual Test Checklist (on-device)
- Full snooze toggle flow with accessibility granted
- Observe WhatsApp Privacy settings flip
- Full offline flow; open WhatsApp and verify "Connecting…" indefinitely
- Duration timer; auto-restore after N minutes
- Daily schedule across midnight boundary
- Quick tile states (active / inactive / unavailable)

---

## 14. Decision Log

| Decision | Chosen | Rejected | Reason |
|----------|--------|----------|--------|
| Framework | Kotlin native | React Native | All APIs deeply native; RN = native modules anyway |
| Read receipt mechanism | AccessibilityService + SharedPrefs trigger | Direct broadcast | Android blocks external broadcasts to AccessibilityService |
| WA launch for snooze | `getLaunchIntentForPackage` | `wa.me` deep-link | `wa.me` opens chat picker, not Settings |
| Network block mechanism | `VpnService` + `addAllowedApplication` | Per-packet UID filter | UID not readable from TUN fd without root |
| Daily alarm | `setExactAndAllowWhileIdle` + self-reschedule | `setRepeating` | `setRepeating` inexact since API 19 |
| Exact alarm fallback | `setWindow` if `canScheduleExactAlarms()` == false | `USE_EXACT_ALARM` | `USE_EXACT_ALARM` restricted to calendar apps; Play policy risk |
| Broadcast security | Signature `<permission>` on `ScheduleReceiver` | `exported=true` unguarded | Prevents alarm spoofing from other apps |
| UI surface | Quick Settings tile (×2) | Home screen widget | Fastest access; purpose-built API |
| Tile listener | Field reference for SharedPreferences listener | Anonymous lambda | WeakReference GC would silently drop the listener |
