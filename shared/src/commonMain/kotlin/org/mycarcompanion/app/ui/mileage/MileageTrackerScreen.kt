package org.mycarcompanion.app.ui.mileage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.mycarcompanion.app.data.models.MileageTrip
import org.mycarcompanion.app.data.models.tripPurposeLabels
import org.mycarcompanion.app.data.models.tripPurposes

class MileageTrackerScreen : Screen {

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: MileageTrackerScreenModel = koinScreenModel()
        val state by model.state.collectAsState()

        Scaffold { paddingValues ->
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(paddingValues).imePadding(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Mileage Tracker",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                TextButton(onClick = { navigator.pop() }) { Text("Back") }
                            }
                        }

                        state.error?.let { error ->
                            item {
                                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        // Active trip card
                        val activeTrip = state.activeTrip
                        if (activeTrip != null) {
                            item {
                                ActiveTripCard(
                                    trip = activeTrip,
                                    endMiles = state.endMiles,
                                    isEnding = state.isEnding,
                                    onEndMilesChange = model::updateEndMiles,
                                    onEndTrip = model::endTrip,
                                )
                            }
                        } else {
                            // Start new trip form
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Start a Trip", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(12.dp))

                                        Text("Purpose *", style = MaterialTheme.typography.labelLarge)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            tripPurposes.forEach { purpose ->
                                                val isSelected = state.form.purpose == purpose
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = { model.updateForm(state.form.copy(purpose = purpose)) },
                                                    label = {
                                                        Text(
                                                            tripPurposeLabels[purpose] ?: purpose,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        )
                                                    },
                                                    leadingIcon = if (isSelected) {
                                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                                    } else null,
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    ),
                                                )
                                            }
                                        }

                                        if (state.vehicles.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("Vehicle", style = MaterialTheme.typography.labelLarge)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                state.vehicles.forEach { vehicle ->
                                                    val isVehicleSelected = state.form.vehicleId == vehicle.id
                                                    FilterChip(
                                                        selected = isVehicleSelected,
                                                        onClick = { model.updateForm(state.form.copy(vehicleId = vehicle.id)) },
                                                        label = {
                                                            Text(
                                                                "${vehicle.year} ${vehicle.make} ${vehicle.model}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = if (isVehicleSelected) FontWeight.Bold else FontWeight.Normal,
                                                            )
                                                        },
                                                        leadingIcon = if (isVehicleSelected) {
                                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                                        } else null,
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        ),
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = state.form.notes,
                                            onValueChange = { model.updateForm(state.form.copy(notes = it)) },
                                            label = { Text("Notes (optional)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = model::startTrip,
                                            enabled = !state.isStarting,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            if (state.isStarting) {
                                                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                                            } else {
                                                Text("Start Trip")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Trip history
                        val completedTrips = state.trips.filter { it.endedAt != null }
                        if (completedTrips.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Trip History",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            items(completedTrips, key = { it.id }) { trip ->
                                TripCard(trip, onDelete = { model.deleteTrip(trip.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveTripCard(
    trip: MileageTrip,
    endMiles: String,
    isEnding: Boolean,
    onEndMilesChange: (String) -> Unit,
    onEndTrip: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Trip in Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Purpose: ${tripPurposeLabels[trip.purpose] ?: trip.purpose}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            trip.notes?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = endMiles,
                onValueChange = onEndMilesChange,
                label = { Text("Miles Driven") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onEndTrip,
                enabled = !isEnding,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isEnding) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("End Trip")
                }
            }
        }
    }
}

@Composable
private fun TripCard(trip: MileageTrip, onDelete: () -> Unit) {
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
                    text = tripPurposeLabels[trip.purpose] ?: trip.purpose,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = trip.startedAt.take(10),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${formatMiles(trip.distanceMiles)} mi",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
            trip.notes?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

private fun formatMiles(value: Double): String {
    val intPart = value.toLong()
    val fracPart = ((value - intPart) * 10 + 0.5).toLong()
    return if (fracPart == 0L) "$intPart" else "$intPart.$fracPart"
}
