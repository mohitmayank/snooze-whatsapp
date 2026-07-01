# Absolute Work Board — Snooze WhatsApp
**Created**: 2026-06-30  
**Status**: in_progress — reopened 2026-07-01 (AW-013/014 test coverage gaps found; AW-015/016/017 tail checklists never actually run)  
**Rollback Point**: (recorded before Wave 1 touches any source files)

---

## Project Conventions

- **Language**: Kotlin (JVM target 17)
- **Build**: Gradle Kotlin DSL (`build.gradle.kts`)
- **UI**: Jetpack Compose + Material 3
- **Min SDK**: 26 | **Target SDK**: 34
- **Package**: `com.mohit.snoozewhatsapp`
- **Test runner**: JUnit4 (unit) + UIAutomator2 / Espresso (instrumented)
- **Linter**: Android Lint (`./gradlew lint`)
- **Verify commands**: `./gradlew assembleDebug` · `./gradlew test` · `./gradlew lint`

---

## Dependency Graph (ASCII)

```
AW-001 (dev env)
  └── AW-002 (scaffold)
        ├── AW-003 (PrefsRepository) ── AW-004 (Feature+Controller)
        │                                      ├── AW-005 (VpnService)    ─┐
        │                                      ├── AW-006 (A11yService)   ─┤ Wave 5 parallel
        │                                      ├── AW-007 (ScheduleMgr)  ─┤
        │                                      └── AW-010 (TileServices)  ─┘
        │                                               │
        │                                      AW-007 ──┤
        │                                               ├── AW-008 (Receivers) ─┐ Wave 6 parallel
        └── AW-011 (PermSetup) ─── AW-012 (MainActivity)┘                      │
                                                                        AW-009 (Worker)  ─┐ Wave 7 parallel
                                                         AW-013 (Unit tests) ─────────────┘
                                                                   │
                                                         AW-014 (Instrumented tests)
                                                                   │
                                               AW-015 → AW-016 → AW-017  (tail: review→validate→verify)
```

---

## Wave Plan

| Wave | Tasks | Mode |
|------|-------|------|
| 1 | AW-001 | sequential |
| 2 | AW-002 | sequential |
| 3 | AW-003 ‖ AW-011 | parallel (disjoint: `data/` vs `ui/`) |
| 4 | AW-004 | sequential |
| 5 | AW-005 ‖ AW-006 ‖ AW-007 ‖ AW-010 | parallel (disjoint: `vpn/`, `accessibility/`, `scheduler/`, `tile/`) |
| 6 | AW-008 ‖ AW-012 | parallel (disjoint: `scheduler/` receivers vs `ui/`) |
| 7 | AW-009 ‖ AW-013 | parallel (disjoint: `scheduler/` worker vs `test/`) |
| 8 | AW-014 | sequential |
| Tail | AW-015 → AW-016 → AW-017 | sequential |

---

## Tasks

### AW-001 — Android Dev Environment Setup
- **Type**: infra | **Size**: S | **Deps**: none | **Status**: `completed`
- **Description**: Install Android Studio (snap), Android SDK Platform 34, Build-Tools 34, Platform-Tools (adb). Add adb to PATH in `.zshrc`. Initialize git repo in project directory.
- **Files**: `~/.zshrc` (PATH), system-level installs only — no project files touched
- **Acceptance criteria**:
  - `android-studio` launches
  - `adb --version` works in new shell
  - `~/Android/Sdk/platforms/android-34` exists
  - `git status` works in project dir
- **Test cases**: run `adb --version`; run `java -version` (should be 21)

---

### AW-002 — Android Project Scaffold
- **Type**: infra | **Size**: M | **Deps**: AW-001 | **Status**: `completed`
- **Description**: Create the full Gradle Android project structure using Android Studio's "New Project" wizard (or `gradle init`). Configure `build.gradle.kts` with all dependencies, set up `settings.gradle.kts`, create base `AndroidManifest.xml` (permissions + placeholders), add Material 3 theme in `res/`, create placeholder vector drawables `ic_snooze.xml` and `ic_offline.xml`, initialize git and add `.gitignore`.
- **Files to create**:
  - `app/build.gradle.kts`
  - `build.gradle.kts`
  - `settings.gradle.kts`
  - `gradle/libs.versions.toml` (version catalog)
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/res/values/themes.xml`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/xml/accessibility_service_config.xml`
  - `app/src/main/res/drawable/ic_snooze.xml`
  - `app/src/main/res/drawable/ic_offline.xml`
  - `.gitignore`
- **Acceptance criteria**:
  - `./gradlew assembleDebug` compiles successfully (even with empty activities)
  - Manifest contains all permissions from spec §10
  - All service placeholders declared (VPN, Accessibility, 2× TileService, 2× Receiver)

---

### AW-003 — PrefsRepository
- **Type**: code | **Size**: S | **Deps**: AW-002 | **Status**: `completed`
- **Description**: Implement `PrefsRepository.kt` — a SharedPreferences singleton wrapping all persisted state. Exposes typed getters/setters for every key defined in spec §3. Include a `get(context)` companion object factory.
- **Files**: `app/src/main/kotlin/com/mohit/snoozewhatsapp/data/PrefsRepository.kt`
- **Test file**: `app/src/test/kotlin/.../data/PrefsRepositoryTest.kt` (written first)
- **Keys**:
  ```
  snooze_active, snooze_restore_at, snooze_daily_start, snooze_daily_end, snooze_pending_action
  offline_active, offline_restore_at, offline_daily_start, offline_daily_end, offline_pending_action
  onboarding_done
  ```
- **Acceptance criteria**:
  - Set then get round-trips for every key
  - `registerOnSharedPreferenceChangeListener` exposed for TileService
  - Companion `get(context)` returns singleton (same instance for same context)

---

### AW-004 — Feature Enum + FeatureController
- **Type**: code | **Size**: S | **Deps**: AW-003 | **Status**: `completed`
- **Description**: Define `Feature` enum (`SNOOZE`, `OFFLINE`) with per-feature prefs key prefixes. Implement `FeatureController.kt` with `activate(context, feature)` and `deactivate(context, feature)` — these update `PrefsRepository`, start/stop the relevant service (VPN for OFFLINE, accessibility trigger for SNOOZE), and request tile updates.
- **Files**:
  - `app/src/main/kotlin/.../data/Feature.kt`
  - `app/src/main/kotlin/.../data/FeatureController.kt`
- **Test file**: `app/src/test/kotlin/.../data/FeatureControllerTest.kt`
- **Acceptance criteria**:
  - `FeatureController.activate(ctx, Feature.SNOOZE)` sets `snooze_active=true` in prefs
  - `FeatureController.deactivate(ctx, Feature.OFFLINE)` sets `offline_active=false`
  - Controller dispatches to correct service per feature

---

### AW-005 — WhatsAppVpnService + VpnConflictChecker
- **Type**: code | **Size**: M | **Deps**: AW-004 | **Status**: `completed`
- **Description**: Implement `WhatsAppVpnService.kt` — a `VpnService` subclass that uses `addAllowedApplication("com.whatsapp")` to route only WhatsApp traffic into the tunnel, then drops all packets (read-loop discards). Add persistent foreground notification "WhatsApp offline". Implement `VpnConflictChecker.kt` that checks `ConnectivityManager` for active `TRANSPORT_VPN`. Add manifest service declaration with `foregroundServiceType="specialUse"`.
- **Files**:
  - `app/src/main/kotlin/.../vpn/WhatsAppVpnService.kt`
  - `app/src/main/kotlin/.../utils/VpnConflictChecker.kt`
  - `app/src/main/AndroidManifest.xml` (add VPN service declaration)
- **Test file**: `app/src/test/kotlin/.../utils/VpnConflictCheckerTest.kt`
- **Acceptance criteria**:
  - VPN service starts; Android shows VPN key icon
  - While VPN active: WhatsApp cannot connect (manual test)
  - Other apps unaffected
  - `VpnConflictChecker.isVpnActive()` returns true when mocked `NetworkCapabilities` has `TRANSPORT_VPN`

---

### AW-006 — WhatsAppAccessibilityService
- **Type**: code | **Size**: M | **Deps**: AW-004 | **Status**: `completed`
- **Description**: Implement `WhatsAppAccessibilityService.kt`. On `TYPE_WINDOW_STATE_CHANGED`, check `PrefsRepository.getPendingAction(Feature.SNOOZE)`. If set, navigate the WhatsApp menu tree: find nodes by text (`"Settings"` → `"Account"` → `"Privacy"` → `"Read receipts"` switch) and click. Timeout after 5s → notify user via `NotificationManager`. On success: clear pendingAction, update `snooze_active`, send local broadcast `ACTION_SNOOZE_DONE`. Add manifest declaration referencing `accessibility_service_config.xml`.
- **Files**:
  - `app/src/main/kotlin/.../accessibility/WhatsAppAccessibilityService.kt`
  - `app/src/main/AndroidManifest.xml` (add accessibility service declaration)
- **Key implementation note**: No direct broadcast to the service. Use SharedPreferences as IPC.
- **Acceptance criteria**:
  - With accessibility granted: toggling snooze opens WhatsApp, navigates to Privacy, flips switch, returns
  - Timeout produces user notification, clears pending action
  - Service correctly handles `pendingAction == ""` (no-op)

---

### AW-007 — ScheduleManager
- **Type**: code | **Size**: M | **Deps**: AW-004 | **Status**: `completed`
- **Description**: Implement `ScheduleManager.kt` — `AlarmManager` wrapper. Methods: `scheduleDurationOff(feature, durationMs)`, `scheduleDailyStart(feature, hour, minute)`, `scheduleDailyEnd(feature, hour, minute)`, `cancelAll(feature)`. Uses `setExactAndAllowWhileIdle` if `canScheduleExactAlarms()`, else `setWindow(±10min)`. Includes `nextOccurrenceOf(hour, minute): Long` helper. All `PendingIntent`s use `FLAG_IMMUTABLE`.
- **Files**: `app/src/main/kotlin/.../scheduler/ScheduleManager.kt`
- **Test file**: `app/src/test/kotlin/.../scheduler/ScheduleManagerTest.kt`
- **Acceptance criteria**:
  - `scheduleDurationOff(SNOOZE, 30*60*1000)` schedules alarm ~30min out
  - `nextOccurrenceOf(22, 0)` returns next 22:00 (today if not yet passed, tomorrow if passed)
  - Falls back to `setWindow` when `canScheduleExactAlarms()` returns false (mock test)

---

### AW-008 — ScheduleReceiver + AlarmReceiver
- **Type**: code | **Size**: S | **Deps**: AW-007 | **Status**: `completed`
- **Description**: Implement `ScheduleReceiver.kt` — handles `ACTION_BOOT_COMPLETED` only; re-registers all active alarms from `PrefsRepository`. Implement `AlarmReceiver.kt` (`exported=false`) — handles `ACTION_DURATION_EXPIRE`, `ACTION_DAILY_START`, `ACTION_DAILY_END`; calls `FeatureController.deactivate/activate` and re-schedules next daily alarm. Add both to manifest.
- **Files**:
  - `app/src/main/kotlin/.../scheduler/ScheduleReceiver.kt`
  - `app/src/main/kotlin/.../scheduler/AlarmReceiver.kt`
  - `app/src/main/AndroidManifest.xml` (two receiver declarations)
- **Acceptance criteria**:
  - Send `BOOT_COMPLETED` in instrumented test → alarms re-registered
  - Send `ACTION_DURATION_EXPIRE` intent → feature deactivated in prefs
  - Send `ACTION_DAILY_START` → feature activated + next day's start alarm scheduled

---

### AW-009 — StateCorrectWorker
- **Type**: code | **Size**: S | **Deps**: AW-008 | **Status**: `completed`
- **Description**: Implement `StateCorrectWorker.kt` — a `CoroutineWorker` that checks all features: if `restore_at` is past and feature is still active, deactivates it. Enqueued as `enqueueUniquePeriodicWork("state_correct", KEEP, 15min)` in `SnoozeWhatsAppApp.onCreate()`. Create `SnoozeWhatsAppApp.kt` application class and register in manifest.
- **Files**:
  - `app/src/main/kotlin/.../scheduler/StateCorrectWorker.kt`
  - `app/src/main/kotlin/.../SnoozeWhatsAppApp.kt`
  - `app/src/main/AndroidManifest.xml` (add `android:name=".SnoozeWhatsAppApp"` to `<application>`)
- **Acceptance criteria**:
  - Worker deactivates feature when `restore_at` is in the past and feature is active
  - `enqueueUniquePeriodicWork` with `KEEP` (second enqueue is no-op)

---

### AW-010 — SnoozeTileService + OfflineTileService
- **Type**: code | **Size**: M | **Deps**: AW-004 | **Status**: `completed`
- **Description**: Implement two `TileService` subclasses. Each holds `prefsListener` as a field (prevents GC). `onStartListening` registers listener + calls `updateTile()`. `onStopListening` unregisters. `onClick` calls `FeatureController.activate/deactivate`. Tile shows `STATE_ACTIVE` (with subtitle), `STATE_INACTIVE`, or `STATE_UNAVAILABLE` (if required permission missing). Add manifest declarations.
- **Files**:
  - `app/src/main/kotlin/.../tile/SnoozeTileService.kt`
  - `app/src/main/kotlin/.../tile/OfflineTileService.kt`
  - `app/src/main/AndroidManifest.xml` (two tile service declarations)
- **Acceptance criteria**:
  - Tile state updates when `PrefsRepository` changes
  - Listener NOT garbage-collected between `onStartListening` and state change (use `System.gc()` in test)
  - `STATE_UNAVAILABLE` shown when accessibility not granted (Snooze tile)

---

### AW-011 — PermissionSetupScreen
- **Type**: code | **Size**: S | **Deps**: AW-002 | **Status**: `completed`
- **Description**: Compose screen shown on first launch or when any required permission is revoked. Three checklist rows: Accessibility Service, VPN Consent, Exact Alarm (optional). Each row has a "Grant" button deep-linking to relevant settings. Polls `checkPermissions()` in `onResume`. "Continue" button enabled when Accessibility + VPN granted.
- **Files**: `app/src/main/kotlin/.../ui/PermissionSetupScreen.kt`
- **Acceptance criteria**:
  - Checklist updates without restart when user grants then returns
  - Deep-links open correct system settings screens
  - "Continue" disabled until Accessibility + VPN granted

---

### AW-012 — MainActivity + FeatureCard
- **Type**: code | **Size**: M | **Deps**: AW-004, AW-007, AW-011 | **Status**: `completed`
- **Description**: `MainActivity.kt` routes to `PermissionSetupScreen` (if `onboarding_done == false`) or main screen. Main screen: two `FeatureCard` composables parameterized by `Feature`. Each card: title + `Switch` (calls `FeatureController`), `StatusText` (shows remaining time or "Active"), `DurationChips` ([30m][1h][2h][4h][Custom] — calls `ScheduleManager`), `DailyRangePicker` (two `TimePickerDialog` pickers — calls `ScheduleManager`). Compact `PermissionsBanner` shown if any permission missing post-onboarding.
- **Files**: `app/src/main/kotlin/.../ui/MainActivity.kt`
- **Acceptance criteria**:
  - Toggle switch activates/deactivates feature
  - Duration chip sets timer; StatusText counts down
  - DailyRangePicker saves times to PrefsRepository
  - Banner shows "Setup required" with deep-link if permission revoked

---

### AW-013 — Unit Tests
- **Type**: test | **Size**: S | **Deps**: AW-003, AW-004, AW-007 | **Status**: `in_progress` (2026-07-01: only ScheduleManagerTest + UiUtilsTest exist; missing PrefsRepositoryTest, FeatureControllerTest, VpnConflictCheckerTest)
- **Description**: Write JUnit4 unit tests with mocked Android framework (`mockito-core` + `robolectric`). Cover: `PrefsRepository` round-trips, `ScheduleManager.nextOccurrenceOf` edge cases (past time, midnight boundary), `VpnConflictChecker.isVpnActive` with mocked `NetworkCapabilities`.
- **Files**: `app/src/test/kotlin/com/mohit/snoozewhatsapp/`
- **Acceptance criteria**:
  - `./gradlew test` passes with 0 failures
  - `nextOccurrenceOf(22, 0)` returns tonight if before 10pm, tomorrow if after
  - `nextOccurrenceOf` at exactly 22:00:00 returns tomorrow (not immediate)

---

### AW-014 — Instrumented Tests
- **Type**: test | **Size**: S | **Deps**: AW-005, AW-008 | **Status**: `todo` (2026-07-01: app/src/androidTest/ does not exist — never implemented)
- **Description**: Write on-device instrumented tests. `VpnServiceTest`: start `WhatsAppVpnService`, attempt HTTP request from WhatsApp context (mock), verify blocked. `ScheduleReceiverTest`: send `ACTION_BOOT_COMPLETED` broadcast, verify `ScheduleManager.scheduleDailyStart` called for active schedules. UIAutomator node-map snapshot: launch WhatsApp (if installed), dump accessibility tree to logcat for Settings screen.
- **Files**: `app/src/androidTest/kotlin/com/mohit/snoozewhatsapp/`
- **Acceptance criteria**:
  - `./gradlew connectedAndroidTest` passes (or descriptive skip if WhatsApp not installed)
  - Boot receiver test passes without WhatsApp requirement

---

### AW-015 — Self Code Review (Mandatory Tail)
- **Type**: review | **Size**: S | **Deps**: AW-005..AW-014 | **Status**: `todo` (2026-07-01: checklist below never actually ticked)
- **Checklist**:
  - [ ] No `@SuppressLint` or `lint:ignore` without justification
  - [ ] All `PendingIntent` use `FLAG_IMMUTABLE`
  - [ ] `prefsListener` stored as field in both TileService classes
  - [ ] `AlarmReceiver` is `exported=false`
  - [ ] `ScheduleReceiver` (BOOT_COMPLETED) has no custom `android:permission`
  - [ ] `POST_NOTIFICATIONS` runtime request present for API 33+
  - [ ] VPN foreground notification channel created before `startForeground()`
  - [ ] `WhatsAppVpnService` calls `setUnderlyingNetworks(null)` if applicable
  - [ ] No hardcoded strings (all in `strings.xml`)

---

### AW-016 — Requirements Validation (Mandatory Tail)
- **Type**: review | **Size**: S | **Deps**: AW-015 | **Status**: `todo` (2026-07-01: checklist below never actually ticked)
- **Checklist**: Cross-check every acceptance criterion in spec §13 against implementation:
  - [ ] AC-1: Snooze toggle with accessibility granted
  - [ ] AC-2: Snooze toggle without accessibility
  - [ ] AC-3: Offline toggle, no VPN conflict
  - [ ] AC-4: Offline toggle, VPN conflict → error dialog
  - [ ] AC-5: 30m duration auto-restore
  - [ ] AC-6: Daily 22:00–08:00 schedule
  - [ ] AC-7: Reboot + schedule persists
  - [ ] AC-8: Quick tile tap
  - [ ] AC-9: Long-press tile → MainActivity
  - [ ] AC-10: SCHEDULE_EXACT_ALARM not granted → setWindow fallback

---

### AW-017 — Full Project Verification (Mandatory Tail)
- **Type**: review | **Size**: S | **Deps**: AW-016 | **Status**: `todo` (2026-07-01: no command output was ever recorded)
- **Commands**:
  ```bash
  ./gradlew assembleDebug     # must exit 0
  ./gradlew test              # must exit 0, 0 failures
  ./gradlew lint              # 0 errors (warnings acceptable)
  ./gradlew installDebug      # installs on connected device
  adb logcat -s SnoozeWA      # watch for errors on first launch
  ```
- **Manual smoke test**: Launch app → complete onboarding → toggle Snooze → observe WhatsApp navigate and flip setting → toggle Offline → observe WhatsApp loses connectivity → set 30m timer → wait → observe auto-restore.

---

## Deferred Work

- Home screen widget (AppWidgetProvider) — decided against, quick tile preferred
- Play Store release / signing config
- Multiple WhatsApp accounts (WA Business)
- iOS support
- Notification action buttons (stop timer from notification)

---

## Rollback Point

`e9578018bfa8565beedc4fe93e6e96b151bfac2b` (2026-06-30, before any source files)
