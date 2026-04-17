package org.mycarcompanion.app.ui.mechanics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.mycarcompanion.app.data.models.carMakes
import org.mycarcompanion.app.data.models.carModelsByMake
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

@OptIn(ExperimentalMaterial3Api::class)
class CreateMechanicJobScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: CreateMechanicJobScreenModel = koinScreenModel()
        val state by model.state.collectAsState()

        var makeExpanded by remember { mutableStateOf(false) }
        var modelExpanded by remember { mutableStateOf(false) }

        val filteredMakes = remember(state.form.vehicleMake) {
            val query = state.form.vehicleMake.trim().lowercase()
            if (query.isEmpty()) carMakes else carMakes.filter { it.lowercase().contains(query) }
        }

        val availableModels = remember(state.form.vehicleMake) {
            carModelsByMake[state.form.vehicleMake] ?: emptyList()
        }

        val filteredModels = remember(state.form.vehicleModel, availableModels) {
            val query = state.form.vehicleModel.trim().lowercase()
            if (query.isEmpty()) availableModels else availableModels.filter { it.lowercase().contains(query) }
        }

        LaunchedEffect(state.createdJob) {
            state.createdJob?.let { job ->
                navigator.replace(MechanicJobDetailScreen(jobId = job.id))
            }
        }

        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("New Job", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { navigator.pop() }) { Text("Cancel") }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Client Info ──────────────────────────────────────────────
                Text("Client Info", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.form.clientName,
                    onValueChange = { model.updateForm(state.form.copy(clientName = it)) },
                    label = { Text("Client Name *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.form.clientEmail,
                    onValueChange = { model.updateForm(state.form.copy(clientEmail = it)) },
                    label = { Text("Client Email (for invite)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    supportingText = { Text("An invite will be sent to join the app") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ── Vehicle Info ─────────────────────────────────────────────
                Text("Vehicle Info", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                // Make dropdown
                ExposedDropdownMenuBox(
                    expanded = makeExpanded && filteredMakes.isNotEmpty(),
                    onExpandedChange = { makeExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = state.form.vehicleMake,
                        onValueChange = {
                            model.updateForm(state.form.copy(vehicleMake = it, vehicleModel = ""))
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
                                    model.updateForm(state.form.copy(vehicleMake = make, vehicleModel = ""))
                                    makeExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Model dropdown
                ExposedDropdownMenuBox(
                    expanded = modelExpanded && filteredModels.isNotEmpty(),
                    onExpandedChange = { modelExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = state.form.vehicleModel,
                        onValueChange = {
                            model.updateForm(state.form.copy(vehicleModel = it))
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
                                    model.updateForm(state.form.copy(vehicleModel = vehicleModel))
                                    modelExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.form.vehicleYear,
                        onValueChange = { model.updateForm(state.form.copy(vehicleYear = it)) },
                        label = { Text("Year *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.form.vehicleColor,
                        onValueChange = { model.updateForm(state.form.copy(vehicleColor = it)) },
                        label = { Text("Color") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.form.vehicleLicensePlate,
                    onValueChange = { model.updateForm(state.form.copy(vehicleLicensePlate = it)) },
                    label = { Text("License Plate") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.form.vehicleVin,
                    onValueChange = { model.updateForm(state.form.copy(vehicleVin = it)) },
                    label = { Text("VIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ── Job Details ──────────────────────────────────────────────
                Text("Job Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.form.description,
                    onValueChange = { model.updateForm(state.form.copy(description = it)) },
                    label = { Text("Job Description") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.form.notes,
                    onValueChange = { model.updateForm(state.form.copy(notes = it)) },
                    label = { Text("Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                state.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = model::save,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Create Job")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
