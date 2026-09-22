package org.mycarcompanion.app.ui.mechanics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.mycarcompanion.app.data.models.CannedJob
import org.mycarcompanion.app.data.models.approvalMethodLabels
import org.mycarcompanion.app.ui.formatMoney

/** Asks the mechanic how the customer gave their answer (the legal record of authorization). */
@Composable
fun ApprovalMethodDialog(title: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("How did the customer tell you?", style = MaterialTheme.typography.bodyMedium)
                approvalMethodLabels.filterKeys { it != "in_app" }.forEach { (method, label) ->
                    OutlinedButton(onClick = { onPick(method) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CannedJobPickerDialog(
    jobs: List<CannedJob>,
    onPick: (CannedJob) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved jobs") },
        text = {
            Column {
                if (jobs.isEmpty()) Text("No saved jobs yet. Add line items to a job, then tap \"Save as job\".")
                jobs.forEach { job ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onPick(job) }, modifier = Modifier.weight(1f)) {
                            Text("${job.name} · $${formatMoney(job.lines.sumOf { it.quantity * it.unitPrice })}")
                        }
                        IconButton(onClick = { onDelete(job.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete ${job.name}")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun SaveCannedJobDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save these lines as a job") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name, e.g. Front brakes") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
