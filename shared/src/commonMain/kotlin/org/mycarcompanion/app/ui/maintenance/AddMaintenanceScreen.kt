package org.mycarcompanion.app.ui.maintenance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.mycarcompanion.app.data.models.maintenanceCategories
import org.mycarcompanion.app.platform.CommonParcelable
import org.mycarcompanion.app.ui.components.DatePickerField

data class AddMaintenanceScreen(val vehicleId: String) : Screen, CommonParcelable {

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: AddMaintenanceScreenModel = koinScreenModel()
        val state by model.state.collectAsState()

        LaunchedEffect(Unit) { model.load(vehicleId) }

        LaunchedEffect(state.saved) {
            if (state.saved) navigator.pop()
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
                    Text("Add Maintenance", style = MaterialTheme.typography.headlineMedium)
                    TextButton(onClick = { navigator.pop() }) { Text("Cancel") }
                }

                Spacer(modifier = Modifier.height(24.dp))

                state.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text("Category *", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    maintenanceCategories.forEach { category ->
                        FilterChip(
                            selected = state.form.category == category,
                            onClick = { model.updateForm(state.form.copy(category = category)) },
                            label = { Text(category, style = MaterialTheme.typography.bodySmall) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.form.description,
                    onValueChange = { model.updateForm(state.form.copy(description = it)) },
                    label = { Text("Description *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(modifier = Modifier.height(12.dp))

                val today = remember { Clock.System.now().toLocalDateTime(TimeZone.UTC).date }
                // ponytail: 1-yr self-backdating cap — change the years here. Older genuine
                // history goes through Record Import (mechanic), which is exempt.
                val earliestSelfDate = remember(today) { today.minus(DatePeriod(years = 1)) }
                DatePickerField(
                    value = state.form.date,
                    onValueChange = { model.updateForm(state.form.copy(date = it)) },
                    label = "Date *",
                    minDate = earliestSelfDate,
                    maxDate = today,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.form.mileage,
                    onValueChange = { model.updateForm(state.form.copy(mileage = it)) },
                    label = { Text("Mileage *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                val enteredMileage = state.form.mileage.toIntOrNull()
                val currentOdo = state.currentOdometer
                if (enteredMileage != null && currentOdo != null) {
                    when {
                        enteredMileage > currentOdo -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = state.updateOdometer,
                                onCheckedChange = { model.setUpdateOdometer(it) },
                            )
                            Text(
                                "Update vehicle mileage to $enteredMileage mi",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        enteredMileage < currentOdo -> Text(
                            "Below the vehicle's current odometer ($currentOdo mi). This won't lower the vehicle's mileage.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.form.cost,
                    onValueChange = { model.updateForm(state.form.copy(cost = it)) },
                    label = { Text("Cost") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.form.notes,
                    onValueChange = { model.updateForm(state.form.copy(notes = it)) },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { model.save(vehicleId) },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Save Maintenance Record")
                    }
                }
            }
        }
    }
}
