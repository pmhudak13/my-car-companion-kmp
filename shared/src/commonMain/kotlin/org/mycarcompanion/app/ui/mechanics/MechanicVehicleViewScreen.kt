package org.mycarcompanion.app.ui.mechanics

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import org.mycarcompanion.app.data.models.MaintenanceLog
import org.mycarcompanion.app.platform.topBarWindowInsets
import org.mycarcompanion.app.data.models.Vehicle
import org.mycarcompanion.app.platform.CommonParcelable
import org.mycarcompanion.app.ui.maintenance.AddMaintenanceScreen
import org.mycarcompanion.app.platform.scaffoldContentWindowInsets

data class MechanicVehicleViewScreen(
    val vehicleId: String,
    val assignmentId: String,
) : Screen, CommonParcelable {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: MechanicVehicleViewScreenModel = koinScreenModel()
        val state by model.state.collectAsState()

        LaunchedEffect(vehicleId, assignmentId) { model.load(vehicleId, assignmentId) }
        LaunchedEffect(state.completed) { if (state.completed) navigator.pop() }

        Scaffold(
            contentWindowInsets = scaffoldContentWindowInsets(),
            topBar = {
                TopAppBar(
                    title = {
                        val v = state.vehicle
                        Text(if (v != null) "${v.year} ${v.make} ${v.model}" else "Vehicle")
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    windowInsets = topBarWindowInsets(),
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { navigator.push(AddMaintenanceScreen(vehicleId)) },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Maintenance Log")
                }
            },
        ) { paddingValues ->
            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.error?.let { error ->
                        item {
                            Text(error, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    // Vehicle info card
                    state.vehicle?.let { vehicle ->
                        item { VehicleInfoCard(vehicle) }
                    }

                    // Complete job section
                    state.assignment?.let { assignment ->
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Job Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (!assignment.notes.isNullOrBlank()) {
                                        Text(assignment.notes, style = MaterialTheme.typography.bodyMedium)
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    Text(
                                        "Assigned: ${assignment.assignedAt.take(10)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    if (state.completing) {
                                        CircularProgressIndicator()
                                    } else {
                                        FilledTonalButton(
                                            onClick = { model.completeJob(assignment.id) },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text("Mark Job Complete")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Maintenance history
                    item {
                        Text(
                            "Maintenance History (${state.logs.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (state.logs.isEmpty()) {
                        item {
                            Text(
                                "No maintenance logs yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(state.logs, key = { it.id }) { log ->
                            MaintenanceLogCard(log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleInfoCard(vehicle: Vehicle) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${vehicle.year} ${vehicle.make} ${vehicle.model}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("Odometer: ${vehicle.odometer} ${vehicle.unit}", style = MaterialTheme.typography.bodySmall)
            vehicle.color?.let { Text("Color: $it", style = MaterialTheme.typography.bodySmall) }
            vehicle.licensePlate?.let { Text("Plate: $it", style = MaterialTheme.typography.bodySmall) }
            vehicle.vin?.let { Text("VIN: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun MaintenanceLogCard(log: MaintenanceLog) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(log.category, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(log.date.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (log.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(log.description, style = MaterialTheme.typography.bodySmall)
            }
            if (log.mileage > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text("${log.mileage} mi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
