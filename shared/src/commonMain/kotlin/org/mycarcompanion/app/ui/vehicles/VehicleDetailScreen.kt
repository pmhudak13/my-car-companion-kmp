package org.mycarcompanion.app.ui.vehicles

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.parcelize.Parcelize
import org.mycarcompanion.app.data.models.MaintenanceLog
import org.mycarcompanion.app.data.models.MechanicAssignment
import org.mycarcompanion.app.data.models.Reminder
import org.mycarcompanion.app.data.models.Vehicle
import org.mycarcompanion.app.data.models.reminderTypeLabels
import org.mycarcompanion.app.platform.CommonParcelable
import org.mycarcompanion.app.ui.maintenance.AddMaintenanceScreen
import org.mycarcompanion.app.ui.mechanics.MechanicDirectoryScreen
import org.mycarcompanion.app.ui.reminders.AddReminderScreen

@Parcelize
data class VehicleDetailScreen(val vehicleId: String) : Screen, CommonParcelable {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: VehicleDetailScreenModel = koinScreenModel()
        val state by model.state.collectAsState()

        LaunchedEffect(vehicleId) { model.load(vehicleId) }
        LaunchedEffect(state.deleted) {
            if (state.deleted) navigator.pop()
        }

        Scaffold(
            floatingActionButton = {
                if (state.vehicle != null) {
                    FloatingActionButton(
                        onClick = { navigator.push(AddMaintenanceScreen(vehicleId)) },
                        containerColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Text("+", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        ) { paddingValues ->
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null && state.vehicle == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { model.load(vehicleId) }) { Text("Retry") }
                    }
                }
                else -> {
                    val vehicle = state.vehicle ?: return@Scaffold
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { VehicleHeader(vehicle, onBack = { navigator.pop() }) }
                        item { VehicleInfo(vehicle) }

                        // --- Reminders Section ---
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Reminders",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                TextButton(
                                    onClick = { navigator.push(AddReminderScreen(vehicleId)) },
                                ) {
                                    Text("+ Add")
                                }
                            }
                        }
                        if (state.reminders.isEmpty()) {
                            item {
                                Text(
                                    "No reminders set. Add one to stay on top of service.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                            }
                        } else {
                            items(state.reminders, key = { it.id }) { reminder ->
                                ReminderCard(reminder, onDelete = { model.deleteReminder(reminder.id) })
                            }
                        }

                        // --- Assigned Mechanics Section ---
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Assigned Mechanics",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                TextButton(
                                    onClick = { navigator.push(MechanicDirectoryScreen()) },
                                ) {
                                    Text("Find")
                                }
                            }
                        }
                        if (state.assignments.isEmpty()) {
                            item {
                                Text(
                                    "No mechanics assigned. Tap Find to browse.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                            }
                        } else {
                            items(state.assignments, key = { it.id }) { assignment ->
                                AssignmentCard(assignment, onRevoke = { model.revokeAssignment(assignment.id) })
                            }
                        }

                        // --- Maintenance Section ---
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Maintenance History",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                TextButton(
                                    onClick = model::deleteVehicle,
                                ) {
                                    Text("Delete Vehicle", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        if (state.logs.isEmpty()) {
                            item {
                                Text(
                                    "No maintenance records yet. Tap + to add one.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        } else {
                            items(state.logs, key = { it.id }) { log ->
                                MaintenanceLogCard(log, onDelete = { model.deleteLog(log.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleHeader(vehicle: Vehicle, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) { Text("< Back") }
        Text(
            text = "${vehicle.year} ${vehicle.make} ${vehicle.model}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun VehicleInfo(vehicle: Vehicle) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            InfoRow("Odometer", "${vehicle.odometer} ${vehicle.unit}")
            vehicle.color?.let { InfoRow("Color", it) }
            vehicle.licensePlate?.let { InfoRow("License Plate", it) }
            vehicle.vin?.let { InfoRow("VIN", it) }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MaintenanceLogCard(log: MaintenanceLog, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = log.category,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = log.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(log.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row {
                    Text(
                        "${log.mileage} mi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    log.cost?.let { cost ->
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            "$${formatCost(cost)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
            log.notes?.let { notes ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun ReminderCard(reminder: Reminder, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (reminder.type == "custom") reminder.customName ?: "Custom" else reminderTypeLabels[reminder.type] ?: reminder.type,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (!reminder.isActive) {
                    Text(
                        text = "Inactive",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    reminder.nextDueDate?.let {
                        Text("Due: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    reminder.nextDueMileage?.let {
                        Text("Due at: $it mi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (reminder.intervalMonths != null || reminder.intervalMiles != null) {
                        val parts = mutableListOf<String>()
                        reminder.intervalMonths?.let { parts.add("$it mo") }
                        reminder.intervalMiles?.let { parts.add("$it mi") }
                        Text("Repeats every ${parts.joinToString(" / ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun AssignmentCard(assignment: MechanicAssignment, onRevoke: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Mechanic Assigned",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Since ${assignment.assignedAt.take(10)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                assignment.notes?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            TextButton(onClick = onRevoke) {
                Text("Revoke", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun formatCost(value: Double): String {
    val intPart = value.toLong()
    val fracPart = ((value - intPart) * 100 + 0.5).toLong()
    return "$intPart.${fracPart.toString().padStart(2, '0')}"
}
