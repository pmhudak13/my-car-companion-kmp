package org.mycarcompanion.app.ui.mechanics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.koin.compose.koinInject
import org.mycarcompanion.app.data.models.MechanicFollowUp
import org.mycarcompanion.app.data.models.MechanicJobNote
import org.mycarcompanion.app.data.models.approvalMethodLabels
import org.mycarcompanion.app.data.repository.JobNotesRepository
import org.mycarcompanion.app.data.repository.MechanicJobRepository
import org.mycarcompanion.app.ui.components.DatePickerField

/**
 * Notes the customer acknowledges plus follow-ups, for one job. Mechanic view adds, records
 * acknowledgments and manages follow-ups; owner view ([isMechanic] false) reads and acknowledges.
 */
@Composable
fun JobNotesSection(jobId: String, isMechanic: Boolean, clientEmail: String? = null) {
    val repo: JobNotesRepository = koinInject()
    val jobRepo: MechanicJobRepository = koinInject()
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf<List<MechanicJobNote>>(emptyList()) }
    var followUps by remember { mutableStateOf<List<MechanicFollowUp>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var noteText by remember { mutableStateOf("") }
    var ackPrompt by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            notes = repo.getNotes(jobId).getOrNull().orEmpty()
            followUps = repo.getFollowUps(jobId).getOrNull().orEmpty()
        }
    }

    /** Runs [action], reloads on success, shows the error otherwise. */
    fun act(action: suspend () -> Result<*>) {
        scope.launch {
            busy = true
            action().onSuccess { error = null; reload() }.onFailure { error = it.message ?: "Something went wrong" }
            busy = false
        }
    }

    LaunchedEffect(jobId) { reload() }
    // owners only see the section once the mechanic has put something in it
    if (!isMechanic && notes.isEmpty() && followUps.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isMechanic || notes.isNotEmpty()) Text("Notes from the mechanic".takeIf { !isMechanic } ?: "Notes (${notes.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (isMechanic) {
            Text(
                "Add a note at any stage, like something you found mid-job. The customer taps \"I acknowledge\" and it's kept with the job.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        notes.forEach { note ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(note.body, style = MaterialTheme.typography.bodyMedium)
                    Text(note.createdAt.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    val ackAt = note.acknowledgedAt
                    if (ackAt != null) {
                        Text(
                            "✓ Acknowledged ${approvalMethodLabels[note.acknowledgeMethod]?.lowercase() ?: ""} on ${ackAt.take(10)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isMechanic) {
                                TextButton(onClick = { ackPrompt = note.id }, enabled = !busy) { Text("Customer acknowledged") }
                                TextButton(onClick = { act { repo.deleteNote(note.id) } }, enabled = !busy) { Text("Delete") }
                            } else {
                                Button(onClick = { act { repo.acknowledgeNote(note.id) } }, enabled = !busy) { Text("I acknowledge") }
                            }
                        }
                    }
                }
            }
        }
        if (isMechanic) {
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("New note") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val text = noteText
                    act {
                        repo.addNote(jobId, text).onSuccess {
                            noteText = ""
                            clientEmail?.let { jobRepo.triggerJobUpdatePushPublic(it, "New note on your job", "Your mechanic added a note. Open the app to acknowledge it.") }
                        }
                    }
                },
                enabled = !busy && noteText.isNotBlank(),
            ) { Text("Add note") }
        }

        if (isMechanic || followUps.isNotEmpty()) {
            Text("Follow-ups", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        }
        followUps.forEach { f ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(f.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        listOfNotNull(
                            if (f.doneAt != null) "Done" else "Due ${f.dueDate}",
                            f.discountPercent?.let { "$it% off if done by then" },
                            if (isMechanic && f.reminderId == null) "Only you get this reminder (customer isn't on the app)" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isMechanic && f.doneAt == null) {
                    TextButton(onClick = { act { repo.completeFollowUp(f.id) } }, enabled = !busy) { Text("Done") }
                }
            }
        }
        if (isMechanic) FollowUpForm(busy = busy) { title, date, discount -> act { repo.addFollowUp(jobId, title, date, discount) } }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }

    ackPrompt?.let { noteId ->
        ApprovalMethodDialog(
            title = "Record acknowledgment",
            onPick = { method -> ackPrompt = null; act { repo.acknowledgeNote(noteId, method) } },
            onDismiss = { ackPrompt = null },
        )
    }
}

@Composable
private fun FollowUpForm(busy: Boolean, onAdd: (title: String, date: String, discount: Int?) -> Unit) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("What to check, e.g. \"Rear brakes at 50%\"") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30 to "30 days", 90 to "3 months", 180 to "6 months").forEach { (days, label) ->
                AssistChip(onClick = { date = today.plus(DatePeriod(days = days)).toString() }, label = { Text(label) })
            }
        }
        DatePickerField(value = date, onValueChange = { date = it }, label = "Remind on", minDate = today)
        OutlinedTextField(
            value = discount,
            onValueChange = { v -> discount = v.filter(Char::isDigit).take(3) },
            label = { Text("Discount if they come back by then (%)") },
            supportingText = { Text("Optional. Shown on the customer's reminder.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                onAdd(title, date, discount.toIntOrNull()?.takeIf { it in 1..100 })
                title = ""; date = ""; discount = ""
            },
            enabled = !busy && title.isNotBlank() && date.isNotBlank(),
        ) { Text("Add follow-up") }
    }
}

/** Dashboard nudge: follow-ups due in the next week or overdue, with who to call. Hidden when none. */
@Composable
fun FollowUpsDueCard(jobs: List<org.mycarcompanion.app.data.models.MechanicJob>, onOpenJob: (String) -> Unit) {
    val repo: JobNotesRepository = koinInject()
    var due by remember { mutableStateOf<List<MechanicFollowUp>>(emptyList()) }
    LaunchedEffect(jobs) {
        val cutoff = Clock.System.todayIn(TimeZone.currentSystemDefault()).plus(DatePeriod(days = 7)).toString()
        // ISO dates compare correctly as strings
        due = repo.getMyOpenFollowUps().getOrNull().orEmpty().filter { it.dueDate <= cutoff }
    }
    if (due.isEmpty()) return
    val byId = jobs.associateBy { it.id }
    Card(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Follow-ups due", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            due.forEach { f ->
                val job = byId[f.mechanicJobId]
                TextButton(onClick = { onOpenJob(f.mechanicJobId) }) {
                    Text(
                        "${f.dueDate}: ${f.title}" + (job?.let { " · ${it.clientName}, ${it.vehicleYear} ${it.vehicleMake} ${it.vehicleModel}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
