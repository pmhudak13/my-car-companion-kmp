package org.mycarcompanion.app.ui.vehicles

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

class AddVehicleScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: AddVehicleScreenModel = koinScreenModel()
        val state by model.state.collectAsState()

        LaunchedEffect(state.saved) {
            if (state.saved) navigator.pop()
        }

        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Add Vehicle",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    TextButton(onClick = { navigator.pop() }) {
                        Text("Cancel")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                state.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = state.form.year,
                    onValueChange = { model.updateForm(state.form.copy(year = it)) },
                    label = { Text("Year *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.form.make,
                    onValueChange = { model.updateForm(state.form.copy(make = it)) },
                    label = { Text("Make *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.form.model,
                    onValueChange = { model.updateForm(state.form.copy(model = it)) },
                    label = { Text("Model *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.form.color,
                    onValueChange = { model.updateForm(state.form.copy(color = it)) },
                    label = { Text("Color") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.form.licensePlate,
                    onValueChange = { model.updateForm(state.form.copy(licensePlate = it)) },
                    label = { Text("License Plate") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.form.vin,
                    onValueChange = { model.updateForm(state.form.copy(vin = it)) },
                    label = { Text("VIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.form.odometer,
                    onValueChange = { model.updateForm(state.form.copy(odometer = it)) },
                    label = { Text("Odometer") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.form.unit == "miles",
                        onClick = { model.updateForm(state.form.copy(unit = "miles")) },
                        label = { Text("Miles") },
                    )
                    FilterChip(
                        selected = state.form.unit == "km",
                        onClick = { model.updateForm(state.form.copy(unit = "km")) },
                        label = { Text("Kilometers") },
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = model::save,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Save Vehicle")
                    }
                }
            }
        }
    }
}
