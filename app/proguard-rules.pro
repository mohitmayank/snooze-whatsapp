# Add project specific ProGuard rules here.

# Accessibility/VPN services are instantiated reflectively by the OS via manifest component
# names, not referenced directly in code — R8 would otherwise strip or rename them.
-keep class com.mohit.snoozewhatsapp.accessibility.WhatsAppAccessibilityService { *; }
-keep class com.mohit.snoozewhatsapp.vpn.WhatsAppVpnService { *; }
-keep class com.mohit.snoozewhatsapp.tile.SnoozeTileService { *; }
-keep class com.mohit.snoozewhatsapp.tile.OfflineTileService { *; }
-keep class com.mohit.snoozewhatsapp.scheduler.ScheduleReceiver { *; }
-keep class com.mohit.snoozewhatsapp.scheduler.AlarmReceiver { *; }
-keep class com.mohit.snoozewhatsapp.scheduler.StateCorrectWorker { *; }

