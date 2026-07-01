package com.mohit.snoozewhatsapp.ui

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mohit.snoozewhatsapp.R
import com.mohit.snoozewhatsapp.data.Feature
import com.mohit.snoozewhatsapp.data.FeatureController
import com.mohit.snoozewhatsapp.data.PrefsRepository
import com.mohit.snoozewhatsapp.scheduler.ScheduleManager

class MainActivity : ComponentActivity() {

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore result — notification is optional enhancement */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Trigger recomposition to re-check permission state
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val prefs = remember { PrefsRepository.get(context) }
    var onboardingDone by remember { mutableStateOf(prefs.onboardingDone) }
    var permState by remember { mutableStateOf(checkPermissions(context)) }

    if (!onboardingDone) {
        PermissionSetupScreen(onComplete = {
            prefs.onboardingDone = true
            onboardingDone = true
            permState = checkPermissions(context)
        })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        if (!permState.accessibilityGranted || !permState.vpnGranted) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = stringResource(R.string.banner_setup_required),
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        FeatureCard(feature = Feature.SNOOZE)
        FeatureCard(feature = Feature.OFFLINE)
    }
}

@Composable
fun FeatureCard(feature: Feature) {
    val context = LocalContext.current
    val prefs = remember { PrefsRepository.get(context) }

    var isActive by remember {
        mutableStateOf(prefs.isActive(feature))
    }
    var restoreAt by remember {
        mutableStateOf(prefs.getRestoreAt(feature))
    }
    var dailyStart by remember {
        mutableStateOf(prefs.getDailyStart(feature))
    }
    var dailyEnd by remember {
        mutableStateOf(prefs.getDailyEnd(feature))
    }

    // Observe prefs changes
    DisposableEffect(feature) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            isActive = prefs.isActive(feature)
            restoreAt = prefs.getRestoreAt(feature)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Title + toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(if (feature == Feature.SNOOZE) R.string.snooze_card_title else R.string.offline_card_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(if (feature == Feature.SNOOZE) R.string.snooze_card_subtitle else R.string.offline_card_subtitle),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = isActive,
                    onCheckedChange = { checked ->
                        if (checked) FeatureController.activate(context, feature)
                        else FeatureController.deactivate(context, feature)
                    }
                )
            }

            // Status
            if (isActive) {
                val statusText = if (restoreAt > 0) {
                    stringResource(R.string.status_restores_in, formatTimeRemaining(restoreAt))
                } else {
                    stringResource(R.string.status_active)
                }
                Text(text = statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider()

            // Duration chips
            Text("Duration", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val durations = listOf(30L to "30m", 60L to "1h", 120L to "2h", 240L to "4h")
                durations.forEach { (mins, label) ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            ScheduleManager.scheduleDurationOff(context, feature, mins * 60_000L)
                            FeatureController.activate(context, feature)
                        },
                        label = { Text(label) }
                    )
                }
            }

            // Daily schedule
            Text(stringResource(R.string.schedule_daily_label), style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = {
                    val (h, m) = if (dailyStart.isNotEmpty()) dailyStart.split(":").map { it.toInt() } else listOf(22, 0)
                    TimePickerDialog(context, { _, hour, minute ->
                        val hhmm = "%02d:%02d".format(hour, minute)
                        prefs.setDailyStart(feature, hhmm)
                        ScheduleManager.scheduleDailyStart(context, feature, hour, minute)
                        dailyStart = hhmm
                    }, h, m, true).show()
                }) {
                    Text(if (dailyStart.isNotEmpty()) dailyStart else "Start")
                }
                Text("→")
                OutlinedButton(onClick = {
                    val (h, m) = if (dailyEnd.isNotEmpty()) dailyEnd.split(":").map { it.toInt() } else listOf(8, 0)
                    TimePickerDialog(context, { _, hour, minute ->
                        val hhmm = "%02d:%02d".format(hour, minute)
                        prefs.setDailyEnd(feature, hhmm)
                        ScheduleManager.scheduleDailyEnd(context, feature, hour, minute)
                        dailyEnd = hhmm
                    }, h, m, true).show()
                }) {
                    Text(if (dailyEnd.isNotEmpty()) dailyEnd else "End")
                }
                if (dailyStart.isNotEmpty() || dailyEnd.isNotEmpty()) {
                    TextButton(onClick = {
                        ScheduleManager.cancelDaily(context, feature)
                        dailyStart = ""
                        dailyEnd = ""
                    }) { Text("Clear") }
                }
            }
        }
    }
}
