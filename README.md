# Snooze WhatsApp

A native Android app that gives you quick, no-root control over two things WhatsApp doesn't let you toggle from the system UI:

1. **Snooze** — turn off Read Receipts (the blue ticks), so contacts can't see when you've read their messages.
2. **Offline** — cut WhatsApp's network access entirely, so it can't send or receive anything, while every other app on your phone keeps working normally.

Both features can be flipped instantly from the app, from a Quick Settings tile, or scheduled (a fixed duration like "30 minutes", or a daily time range like "22:00–08:00").

Package: `com.mohit.snoozewhatsapp` · minSdk 26 (Android 8) · targetSdk 34 (Android 14).

For the full technical design (how the accessibility navigation and VPN tunnel work internally), see [`docs/plans/2026-06-30-snooze-whatsapp-design.md`](docs/plans/2026-06-30-snooze-whatsapp-design.md).

## How it works

- **Snooze** uses an `AccessibilityService` that briefly opens WhatsApp in the background, navigates Settings → Account → Privacy, and flips the Read Receipts switch — the same thing you'd do by hand, just automated. WhatsApp exposes no public API for this.
- **Offline** starts a local `VpnService` tunnel that only WhatsApp's traffic is routed into (`addAllowedApplication`), then silently drops every packet that enters it. WhatsApp appears offline; nothing else on your device is affected.

Neither feature requires root.

## Install

### Option A — build it yourself (see [Build](#build) below)
Produces a signed APK at `app/build/outputs/apk/release/app-release.apk`.

### Option B — install a prebuilt APK on your device
```bash
adb install app/build/outputs/apk/release/app-release.apk
```
Or copy the APK to your phone and open it — you'll need to allow "install from this source" the first time.

## Set up (one-time)

On first launch the app walks you through a permissions checklist. All three require manual action — Android doesn't auto-grant any of them:

1. **Allow restricted setting** *(Android 13+ only)* — sideloaded apps have Accessibility access blocked by default ("Restricted setting"). Tap **Open**, then in the app's system settings page allow it manually before step 2 will work.
2. **Accessibility Service** — required for Snooze to toggle Read Receipts. Tap **Open** → find "Snooze WhatsApp" → enable it.
3. **VPN permission** — required for Offline. Tap **Open** and accept Android's "Connection request" dialog (shown once).
4. **Exact alarm permission** *(Android 12+ only)* — optional; without it, scheduled toggles fire within a ~10-minute window instead of exactly on time.

You can finish setup once Accessibility + VPN are granted (exact alarms can be skipped). Re-open this checklist any time via the banner shown on the main screen if a permission gets revoked.

WhatsApp must be installed on the device — both features are inert without it.

## Build

Requires JDK 17+. The `keystore.properties` + `keystore/release.jks` files in this repo (git-ignored) hold the release signing credentials.

```bash
# Debug build (unsigned, for a connected device via adb)
./gradlew assembleDebug
./gradlew installDebug

# Signed release APK → app/build/outputs/apk/release/app-release.apk
./gradlew assembleRelease

# Signed release bundle for Play Store → app/build/outputs/bundle/release/app-release.aab
./gradlew bundleRelease
```

If `keystore.properties` is missing (e.g. a fresh clone without the signing key), `assembleRelease` still produces an **unsigned** APK.

Run unit tests with `./gradlew test`.

## Use

**Main screen** — one card per feature (Snooze, Offline), each with:
- A **switch** to toggle it on/off immediately.
- **Duration chips** (30m / 1h / 2h / 4h) — activates the feature and auto-restores after that time.
- A **daily range** (Start → End time pickers) — activates/deactivates automatically every day at those times; tap **Clear** to remove it.
- A status line showing e.g. "Active — restores in 47m" while a timer is running.

Schedules and active state survive a reboot and app kill (`AlarmManager` + a `WorkManager` fallback that corrects drift every 15 minutes if an exact alarm was missed, e.g. under battery-saver Doze).

**Quick Settings tiles** — pull down the notification shade twice, edit your tiles, and add "WA Snooze" and "WA Offline". Tap a tile to toggle the feature; long-press to jump into the app.

**Settings** (gear icon, top right) — currently one toggle: **"Show test duration (1m)"**, which adds a 1-minute duration chip next to the normal ones, for quickly verifying that auto-restore works without waiting 30+ minutes.

### Notes
- If Offline detects another VPN already active on the device, it refuses to start and shows an error — only one VPN tunnel can run at a time on Android.
- If WhatsApp changes its Settings UI layout in an update, Snooze's navigation may fail; you'll get a notification asking you to toggle Read Receipts manually, and no permanent damage — it just times out after 5 seconds and clears the pending action.
