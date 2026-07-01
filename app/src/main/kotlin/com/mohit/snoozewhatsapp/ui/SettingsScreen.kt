package com.mohit.snoozewhatsapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mohit.snoozewhatsapp.data.PrefsRepository

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PrefsRepository.get(context) }
    var showTestDurations by remember { mutableStateOf(prefs.showTestDurations) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show test duration (1m)", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Adds a 1-minute duration chip for quickly testing auto-restore",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = showTestDurations,
                onCheckedChange = {
                    prefs.showTestDurations = it
                    showTestDurations = it
                }
            )
        }
    }
}
