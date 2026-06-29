package org.mycarcompanion.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

// Read-only date field that opens a calendar dialog. Stores/emits "YYYY-MM-DD".
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Tap to pick a date") },
            trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Pick date") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent overlay so a tap anywhere on the field opens the calendar.
        Box(modifier = Modifier.matchParentSize().clickable { showPicker = true })
    }
    if (showPicker) {
        val initialMillis = remember(value) {
            runCatching { LocalDate.parse(value).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() }.getOrNull()
        }
        val selectableDates = remember(minDate, maxDate) {
            val minMs = minDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()
            val maxMs = maxDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    (minMs == null || utcTimeMillis >= minMs) && (maxMs == null || utcTimeMillis <= maxMs)
                override fun isSelectableYear(year: Int) =
                    (minDate == null || year >= minDate.year) && (maxDate == null || year <= maxDate.year)
            }
        }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = selectableDates,
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        onValueChange(Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date.toString())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
