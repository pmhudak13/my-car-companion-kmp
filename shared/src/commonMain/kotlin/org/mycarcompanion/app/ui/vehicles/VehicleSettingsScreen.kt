package org.mycarcompanion.app.ui.vehicles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.mycarcompanion.app.data.models.carMakes
import org.mycarcompanion.app.data.models.carModelsByMake
import org.mycarcompanion.app.platform.CommonParcelable
import org.mycarcompanion.app.ui.transfer.TransferScreen

data class VehicleSettingsScreen(val vehicleId: String) : Screen, CommonParcelable {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: VehicleSettingsScreenModel = koinScreenModel()
        val state by model.state.collectAsState()

        var makeExpanded by remember { mutableStateOf(false) }
        var modelExpanded by remember { mutableStateOf(false) }

        LaunchedEffect(vehicleId) { model.load(vehicleId) }
        LaunchedEffect(state.saved) { if (state.saved) navigator.pop() }
        LaunchedEffect(state.deleted) { if (state.deleted) navigator.popUntilRoot() }

        val filteredMakes = remember(state.form.make) {
            val query = state.form.make.trim().lowercase()
            if (query.isEmpty()) carMakes else carMakes.filter { it.lowercase().contains(query) }
        }
        val availableModels = remember(state.form.make) {
            carModelsByMake[state.form.make] ?: emptyList()
        }
        val filteredModels = remember(state.form.model, availableModels) {
            val query = state.form.model.trim().lowercase()
            if (query.isEmpty()) availableModels else availableModels.filter { it.lowercase().contains(query) }
        }

        // Delete confirmation dialog
        if (state.showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { model.cancelDelete() },
                title = { Text("Delete Vehicle") },
                text = { Text("This will permanently delete the vehicle and all its maintenance history. This cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = { model.deleteVehicle() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { model.cancelDelete() }) { Text("Cancel") }
                },
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Vehicle Settings") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { paddingValues ->
            when {
                state.loading || state.deleting -> Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Year
                    OutlinedTextField(
                        value = state.form.year,
                        onValueChange = { model.updateForm(state.form.copy(year = it)) },
                        label = { Text("Year *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Make dropdown
                    ExposedDropdownMenuBox(
                        expanded = makeExpanded && filteredMakes.isNotEmpty(),
                        onExpandedChange = { makeExpanded = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = state.form.make,
                            onValueChange = {
                                model.updateForm(state.form.copy(make = it, model = ""))
                                makeExpanded = true
                            },
                            label = { Text("Make *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = makeExpanded) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = makeExpanded && filteredMakes.isNotEmpty(),
                            onDismissRequest = { makeExpanded = false },
                        ) {
                            filteredMakes.forEach { make ->
                                DropdownMenuItem(
                                    text = { Text(make) },
                                    onClick = {
                                        model.updateForm(state.form.copy(make = make, model = ""))
                                        makeExpanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Model dropdown
                    ExposedDropdownMenuBox(
                        expanded = modelExpanded && filteredModels.isNotEmpty(),
                        onExpandedChange = { modelExpanded = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = state.form.model,
                            onValueChange = {
                                model.updateForm(state.form.copy(model = it))
                                modelExpanded = true
                            },
                            label = { Text("Model *") },
                            trailingIcon = {
                                if (availableModels.isNotEmpty()) {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded)
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = modelExpanded && filteredModels.isNotEmpty(),
                            onDismissRequest = { modelExpanded = false },
                        ) {
                            filteredModels.forEach { vehicleModel ->
                                DropdownMenuItem(
                                    text = { Text(vehicleModel) },
                                    onClick = {
                                        model.updateForm(state.form.copy(model = vehicleModel))
                                        modelExpanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Color
                    OutlinedTextField(
                        value = state.form.color,
                        onValueChange = { model.updateForm(state.form.copy(color = it)) },
                        label = { Text("Color") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // License Plate
                    OutlinedTextField(
                        value = state.form.licensePlate,
                        onValueChange = { model.updateForm(state.form.copy(licensePlate = it)) },
                        label = { Text("License Plate") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // VIN
                    OutlinedTextField(
                        value = state.form.vin,
                        onValueChange = { model.updateForm(state.form.copy(vin = it)) },
                        label = { Text("VIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Odometer
                    OutlinedTextField(
                        value = state.form.odometer,
                        onValueChange = { model.updateForm(state.form.copy(odometer = it)) },
                        label = { Text("Odometer") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Unit chips
                    Text("Unit", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        FilterChip(
                            selected = state.form.unit == "miles",
                            onClick = { model.updateForm(state.form.copy(unit = "miles")) },
                            label = { Text("Miles") },
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        FilterChip(
                            selected = state.form.unit == "km",
                            onClick = { model.updateForm(state.form.copy(unit = "km")) },
                            label = { Text("km") },
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { model.save() },
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.height(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Save Changes")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedButton(
                        onClick = {
                            val v = state.vehicle
                            if (v != null) {
                                val label = "${v.year} ${v.make} ${v.model}"
                                navigator.push(TransferScreen(vehicleId = vehicleId, vehicleName = label))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Transfer Vehicle")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { model.showDeleteConfirm() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error),
                        ),
                    ) {
                        Text("Delete Vehicle")
                    }
                }
            }
        }
    }
}
