package com.mohit.snoozewhatsapp.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mohit.snoozewhatsapp.R
import com.mohit.snoozewhatsapp.utils.isAccessibilityEnabled

data class PermissionState(
    val accessibilityGranted: Boolean,
    val vpnGranted: Boolean,
    val exactAlarmGranted: Boolean,
)

fun checkPermissions(context: Context): PermissionState {
    val am = context.getSystemService(AlarmManager::class.java)
    return PermissionState(
        accessibilityGranted = isAccessibilityEnabled(context),
        vpnGranted = VpnService.prepare(context) == null,
        exactAlarmGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        },
    )
}

@Composable
fun PermissionSetupScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(checkPermissions(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        state = checkPermissions(context)
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { state = checkPermissions(context) }

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { state = checkPermissions(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text("Grant these permissions to enable all features.")

        Spacer(Modifier.height(8.dp))

        PermissionRow(
            title = stringResource(R.string.permission_restricted_settings_title),
            description = stringResource(R.string.permission_restricted_settings_desc),
            granted = false,
            buttonLabel = stringResource(R.string.permission_open),
            onGrant = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
            }
        )

        PermissionRow(
            title = stringResource(R.string.permission_accessibility_title),
            description = stringResource(R.string.permission_accessibility_desc),
            granted = state.accessibilityGranted,
            onGrant = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        )

        PermissionRow(
            title = stringResource(R.string.permission_vpn_title),
            description = stringResource(R.string.permission_vpn_desc),
            granted = state.vpnGranted,
            onGrant = {
                val vpnIntent = VpnService.prepare(context)
                if (vpnIntent != null) {
                    vpnLauncher.launch(vpnIntent)
                } else {
                    state = checkPermissions(context)
                }
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PermissionRow(
                title = stringResource(R.string.permission_alarm_title),
                description = stringResource(R.string.permission_alarm_desc),
                granted = state.exactAlarmGranted,
                onGrant = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onComplete,
            enabled = state.accessibilityGranted && state.vpnGranted,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.onboarding_continue))
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    buttonLabel: String = stringResource(R.string.permission_grant),
    onGrant: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.width(8.dp))
            if (granted) {
                Text(
                    text = stringResource(R.string.permission_granted),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(onClick = onGrant) {
                    Text(buttonLabel)
                }
            }
        }
    }
}
