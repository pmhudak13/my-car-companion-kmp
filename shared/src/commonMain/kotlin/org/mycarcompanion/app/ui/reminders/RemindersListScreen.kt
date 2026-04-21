package org.mycarcompanion.app.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.mycarcompanion.app.data.models.Reminder
import org.mycarcompanion.app.data.models.reminderTypeLabels

class RemindersListScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: RemindersListScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        var showVehiclePicker by remember { mutableStateOf(false) }

        // Vehicle picker dialog for FAB when multiple vehicles
        if (showVehiclePicker) {
            AlertDialog(
                onDismissRequest = { showVehiclePicker = false },
                title = { Text("Select Vehicle") },
                text = {
                    Column {
                        state.vehicles.forEach { vehicle ->
                            TextButton(
                                onClick = {
                                    showVehiclePicker = false
                                    navigator.push(AddReminderScreen(vehicle.id))
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("${vehicle.year} ${vehicle.make} ${vehicle.model}")
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showVehiclePicker = false }) { Text("Cancel") }
                },
            )
        }

        // Delete confirmation dialog
        state.deleteConfirmId?.let { id ->
            AlertDialog(
                onDismissRequest = { model.cancelDelete() },
                title = { Text("Delete Reminder") },
                text = { Text("Remove this reminder? This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = { model.deleteReminder(id) }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { model.cancelDelete() }) { Text("Cancel") }
                },
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Reminders") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
            floatingActionButton = {
                if (state.vehicles.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = {
                            when {
                                state.vehicles.size == 1 -> navigator.push(AddReminderScreen(state.vehicles[0].id))
                                else -> showVehiclePicker = true
                            }
                        },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Reminder")
                    }
                }
            },
        ) { paddingValues ->
            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.error != null -> Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    val errorMsg = state.error
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(
                            text = errorMsg ?: "An error occurred",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = model::load) { Text("Retry") }
                    }
                }

                state.vehicles.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Add a vehicle first to create reminders",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    val filtered = model.filteredReminders()
                    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                        // Filter chips
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val filters = listOf("all" to "All", "active" to "Active", "overdue" to "Overdue")
                            items(filters) { (value, label) ->
                                FilterChip(
                                    selected = state.filter == value,
                                    onClick = { model.setFilter(value) },
                                    label = { Text(label) },
                                )
                            }
                        }

                        if (filtered.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "No reminders",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(filtered, key = { it.id }) { reminder ->
                                    ReminderCard(
                                        reminder = reminder,
                                        vehicleName = model.vehicleName(reminder.vehicleId),
                                        onDelete = { model.confirmDelete(reminder.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: Reminder,
    vehicleName: String,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.customName
                        ?: reminderTypeLabels[reminder.type]
                        ?: reminder.type,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = vehicleName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (reminder.nextDueDate != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Due: ${reminder.nextDueDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (reminder.nextDueMileage != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Due at: ${reminder.nextDueMileage} mi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!reminder.isActive) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Inactive",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
