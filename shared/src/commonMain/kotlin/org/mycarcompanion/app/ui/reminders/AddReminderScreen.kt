package org.mycarcompanion.app.ui.reminders

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
import org.mycarcompanion.app.data.models.reminderTypeLabels
import org.mycarcompanion.app.data.models.reminderTypes
import org.mycarcompanion.app.platform.CommonParcelable

// Auto-formats raw digit input into YYYY-MM-DD as the user types.
private fun formatDateInput(input: String): String {
    val digits = input.filter { it.isDigit() }.take(8)
    return buildString {
        digits.forEachIndexed { index, c ->
            if (index == 4 || index == 6) append('-')
            append(c)
        }
    }
}

// Returns true only when the string is a complete, well-formed YYYY-MM-DD date.
private fun isValidDate(date: String): Boolean {
    if (date.length != 10) return false
    val parts = date.split("-")
    if (parts.size != 3) return false
    val year = parts[0].toIntOrNull() ?: return false
    val month = parts[1].toIntOrNull() ?: return false
    val day = parts[2].toIntOrNull() ?: return false
    return year in 1900..2100 && month in 1..12 && day in 1..31
}

data class AddReminderScreen(val vehicleId: String) : Screen, CommonParcelable {

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: AddReminderScreenModel = koinScreenModel()
        val state by model.state.collectAsState()

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
                    Text("Add Reminder", style = MaterialTheme.typography.headlineMedium)
                    TextButton(onClick = { navigator.pop() }) { Text("Cancel") }
                }

                Spacer(modifier = Modifier.height(24.dp))

                state.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text("Type *", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    reminderTypes.forEach { type ->
                        FilterChip(
                            selected = state.form.type == type,
                            onClick = { model.updateForm(state.form.copy(type = type)) },
                            label = { Text(reminderTypeLabels[type] ?: type, style = MaterialTheme.typography.bodySmall) },
                        )
                    }
                }

                if (state.form.type == "custom") {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = state.form.customName,
                        onValueChange = { model.updateForm(state.form.copy(customName = it)) },
                        label = { Text("Custom Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Due When", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))

                val dateIsInvalid = state.form.nextDueDate.isNotEmpty() &&
                    !isValidDate(state.form.nextDueDate)
                OutlinedTextField(
                    value = state.form.nextDueDate,
                    onValueChange = { input ->
                        model.updateForm(state.form.copy(nextDueDate = formatDateInput(input)))
                    },
                    label = { Text("Next Due Date") },
                    placeholder = { Text("YYYY-MM-DD") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = dateIsInvalid,
                    supportingText = {
                        if (dateIsInvalid) Text("Enter a valid date (YYYY-MM-DD)")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.form.nextDueMileage,
                    onValueChange = { model.updateForm(state.form.copy(nextDueMileage = it)) },
                    label = { Text("Next Due Mileage") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Recurring Interval (optional)", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.form.intervalMonths,
                    onValueChange = { model.updateForm(state.form.copy(intervalMonths = it)) },
                    label = { Text("Every N Months") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.form.intervalMiles,
                    onValueChange = { model.updateForm(state.form.copy(intervalMiles = it)) },
                    label = { Text("Every N Miles") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { model.save(vehicleId) },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Save Reminder")
                    }
                }
            }
        }
    }
}
