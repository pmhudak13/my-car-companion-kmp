package org.mycarcompanion.app.ui.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

class NotificationsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        // Preferences stored in composition state — persisting across app restarts
        // would require a preferences repository wired to DataStore or Supabase
        var oilChange by rememberSaveable { mutableStateOf(true) }
        var tireRotation by rememberSaveable { mutableStateOf(true) }
        var registration by rememberSaveable { mutableStateOf(true) }
        var customReminders by rememberSaveable { mutableStateOf(true) }
        var newMessages by rememberSaveable { mutableStateOf(true) }
        var mechanicUpdates by rememberSaveable { mutableStateOf(true) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Notifications") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Maintenance Reminders",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        NotifToggle("Oil Change", oilChange) { oilChange = it }
                        Divider()
                        NotifToggle("Tire Rotation", tireRotation) { tireRotation = it }
                        Divider()
                        NotifToggle("Registration", registration) { registration = it }
                        Divider()
                        NotifToggle("Custom Reminders", customReminders) { customReminders = it }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    "Activity",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        NotifToggle("New Messages", newMessages) { newMessages = it }
                        Divider()
                        NotifToggle("Mechanic Updates", mechanicUpdates) { mechanicUpdates = it }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Push notification delivery requires the app to be installed and notification permissions granted on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NotifToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
