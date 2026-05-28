package org.mycarcompanion.app.ui.vehicles

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.saveable.rememberSaveable
import coil3.compose.AsyncImage
import org.mycarcompanion.app.data.models.MaintenanceLog
import org.mycarcompanion.app.data.models.MechanicAssignment
import org.mycarcompanion.app.data.models.MechanicJob
import org.mycarcompanion.app.data.models.MechanicJobIssue
import org.mycarcompanion.app.data.models.MechanicJobMedia
import org.mycarcompanion.app.data.models.Reminder
import org.mycarcompanion.app.data.models.Vehicle
import org.mycarcompanion.app.data.models.reminderTypeLabels
import org.mycarcompanion.app.data.supabase.SupabaseConfig
import org.mycarcompanion.app.platform.CommonParcelable
import org.mycarcompanion.app.ui.maintenance.AddMaintenanceScreen
import org.mycarcompanion.app.ui.mechanics.MechanicDirectoryScreen
import org.mycarcompanion.app.ui.reminders.AddReminderScreen
import org.mycarcompanion.app.platform.scaffoldContentWindowInsets

data class VehicleDetailScreen(val vehicleId: String) : Screen, CommonParcelable {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: VehicleDetailScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        var deleteVehicleConfirm by remember { mutableStateOf(false) }
        var deleteLogId by remember { mutableStateOf<String?>(null) }
        var deleteReminderId by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(vehicleId) { model.load(vehicleId) }
        LaunchedEffect(state.deleted) {
            if (state.deleted) navigator.pop()
        }

        Scaffold(
            contentWindowInsets = scaffoldContentWindowInsets(),
            floatingActionButton = {
                if (state.vehicle != null) {
                    FloatingActionButton(
                        onClick = { navigator.push(AddMaintenanceScreen(vehicleId)) },
                        containerColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Text("+", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        ) { paddingValues ->
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null && state.vehicle == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { model.load(vehicleId) }) { Text("Retry") }
                    }
                }
                else -> {
                    val vehicle = state.vehicle ?: return@Scaffold
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { VehicleHeader(vehicle, onBack = { navigator.pop() }, onSettings = { navigator.push(VehicleSettingsScreen(vehicleId)) }) }
                        item { VehicleInfo(vehicle) }

                        // --- Reminders Section ---
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Reminders",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                TextButton(
                                    onClick = { navigator.push(AddReminderScreen(vehicleId)) },
                                ) {
                                    Text("+ Add")
                                }
                            }
                        }
                        if (state.reminders.isEmpty()) {
                            item {
                                Text(
                                    "No reminders set. Add one to stay on top of service.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                            }
                        } else {
                            items(state.reminders, key = { it.id }) { reminder ->
                                ReminderCard(reminder, onDelete = { deleteReminderId = reminder.id })
                            }
                        }

                        // --- Assigned Mechanics Section ---
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Assigned Mechanics",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                TextButton(
                                    onClick = { navigator.push(MechanicDirectoryScreen(vehicleId = vehicleId)) },
                                ) {
                                    Text("Find")
                                }
                            }
                        }
                        if (state.assignments.isEmpty()) {
                            item {
                                Text(
                                    "No mechanics assigned. Tap Find to browse.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                            }
                        } else {
                            items(state.assignments, key = { it.id }) { assignment ->
                                AssignmentCard(assignment, onRevoke = { model.revokeAssignment(assignment.id) })
                            }
                        }

                        // --- Mechanic Job History ---
                        if (state.mechanicJobs.isNotEmpty()) {
                            item {
                                Text(
                                    "Mechanic Service Jobs (${state.mechanicJobs.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            items(state.mechanicJobs, key = { it.id }) { job ->
                                MechanicJobCard(
                                    job = job,
                                    issues = state.issuesByJobId[job.id] ?: emptyList(),
                                    media = state.mediaByJobId[job.id] ?: emptyList(),
                                    respondingIssueId = state.respondingIssueId,
                                    onRespondToIssue = { issueId, approved ->
                                        model.respondToIssue(issueId, approved, null)
                                    },
                                )
                            }
                        }

                        // --- Maintenance Section ---
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Maintenance History",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                TextButton(
                                    onClick = { deleteVehicleConfirm = true },
                                ) {
                                    Text("Delete Vehicle", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        if (state.logs.isEmpty()) {
                            item {
                                Text(
                                    "No maintenance records yet. Tap + to add one.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        } else {
                            items(state.logs, key = { it.id }) { log ->
                                MaintenanceLogCard(log, onDelete = { deleteLogId = log.id })
                            }
                        }
                    }
                }
            }
        }

        if (deleteVehicleConfirm) {
            AlertDialog(
                onDismissRequest = { deleteVehicleConfirm = false },
                title = { Text("Delete Vehicle") },
                text = { Text("This will permanently delete this vehicle and all its data. Continue?") },
                confirmButton = {
                    Button(
                        onClick = { deleteVehicleConfirm = false; model.deleteVehicle() },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { deleteVehicleConfirm = false }) { Text("Cancel") }
                },
            )
        }

        deleteLogId?.let { logId ->
            AlertDialog(
                onDismissRequest = { deleteLogId = null },
                title = { Text("Delete Log Entry") },
                text = { Text("Remove this maintenance record?") },
                confirmButton = {
                    Button(
                        onClick = { deleteLogId = null; model.deleteLog(logId) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { deleteLogId = null }) { Text("Cancel") }
                },
            )
        }

        deleteReminderId?.let { reminderId ->
            AlertDialog(
                onDismissRequest = { deleteReminderId = null },
                title = { Text("Delete Reminder") },
                text = { Text("Remove this reminder?") },
                confirmButton = {
                    Button(
                        onClick = { deleteReminderId = null; model.deleteReminder(reminderId) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { deleteReminderId = null }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun VehicleHeader(vehicle: Vehicle, onBack: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) { Text("< Back") }
        Text(
            text = "${vehicle.year} ${vehicle.make} ${vehicle.model}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Vehicle Settings")
        }
    }
}

@Composable
private fun VehicleInfo(vehicle: Vehicle) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            InfoRow("Odometer", "${vehicle.odometer} ${vehicle.unit}")
            vehicle.color?.let { InfoRow("Color", it) }
            vehicle.licensePlate?.let { InfoRow("License Plate", it) }
            vehicle.vin?.let { InfoRow("VIN", it) }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MaintenanceLogCard(log: MaintenanceLog, onDelete: () -> Unit) {
    val fromMechanic = log.source == "mechanic"
    val isEdited = fromMechanic && log.updatedAt.isNotBlank() && log.updatedAt != log.createdAt
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = log.category,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (fromMechanic) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "By Mechanic",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = log.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(log.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row {
                    Text(
                        "${log.mileage} mi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    log.cost?.let { cost ->
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            "$${formatCost(cost)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!fromMechanic) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            log.notes?.let { notes ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            if (isEdited) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Edited ${log.updatedAt.take(10)}${if (!log.editNotes.isNullOrBlank()) " · ${log.editNotes}" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
fun ReminderCard(reminder: Reminder, onDelete: () -> Unit) {
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
                    text = if (reminder.type == "custom") reminder.customName ?: "Custom" else reminderTypeLabels[reminder.type] ?: reminder.type,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (!reminder.isActive) {
                    Text(
                        text = "Inactive",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    reminder.nextDueDate?.let {
                        Text("Due: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    reminder.nextDueMileage?.let {
                        Text("Due at: $it mi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (reminder.intervalMonths != null || reminder.intervalMiles != null) {
                        val parts = mutableListOf<String>()
                        reminder.intervalMonths?.let { parts.add("$it mo") }
                        reminder.intervalMiles?.let { parts.add("$it mi") }
                        Text("Repeats every ${parts.joinToString(" / ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun AssignmentCard(assignment: MechanicAssignment, onRevoke: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Mechanic Assigned",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Since ${assignment.assignedAt.take(10)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                assignment.notes?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            TextButton(onClick = onRevoke) {
                Text("Revoke", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun MechanicJobCard(
    job: MechanicJob,
    issues: List<MechanicJobIssue>,
    media: List<MechanicJobMedia>,
    respondingIssueId: String?,
    onRespondToIssue: (issueId: String, approved: Boolean) -> Unit,
) {
    val pendingIssues = issues.filter { it.status == "pending" }
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (pendingIssues.isNotEmpty())
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${job.vehicleYear} ${job.vehicleMake} ${job.vehicleModel}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Created ${job.createdAt.take(10)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (job.status == "open") "In Progress" else "Completed",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (job.status == "open") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }

            // Progress bar
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Progress", style = MaterialTheme.typography.labelMedium)
                Text("${job.progressPercent}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { job.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )

            // Pending issues requiring approval
            if (pendingIssues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "⚠ ${pendingIssues.size} issue${if (pendingIssues.size != 1) "s" else ""} need your approval",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                pendingIssues.forEach { issue ->
                    PendingIssueRow(
                        issue = issue,
                        isResponding = respondingIssueId == issue.id,
                        onApprove = { onRespondToIssue(issue.id, true) },
                        onDecline = { onRespondToIssue(issue.id, false) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Resolved issues (collapsed summary)
            val resolvedIssues = issues.filter { it.status != "pending" }
            if (resolvedIssues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${resolvedIssues.count { it.status == "approved" }} approved · ${resolvedIssues.count { it.status == "declined" }} declined",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Media
            if (media.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                OwnerMediaSection(media = media)
            }
        }
    }
}

@Composable
private fun PendingIssueRow(
    issue: MechanicJobIssue,
    isResponding: Boolean,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(issue.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            issue.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            issue.estimatedCost?.let {
                Text("Estimated cost: $${formatCost(it)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (isResponding) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onApprove, modifier = Modifier.weight(1f)) { Text("Approve") }
                    OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) { Text("Decline") }
                }
            }
        }
    }
}

@Composable
private fun OwnerMediaSection(media: List<MechanicJobMedia>) {
    var showThumbnails by rememberSaveable { mutableStateOf(true) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Photos & Videos (${media.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row {
                TextButton(onClick = { showThumbnails = true }) {
                    Text(
                        "Thumbnails",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (showThumbnails) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { showThumbnails = false }) {
                    Text(
                        "Files",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!showThumbnails) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (showThumbnails) {
            val rows = (media.size + 2) / 3
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(rows) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (col in 0..2) {
                            val idx = row * 3 + col
                            if (idx < media.size) {
                                val item = media[idx]
                                val url = "${SupabaseConfig.url}/storage/v1/object/public/mechanic-job-media/${item.storagePath}"
                                Card(modifier = Modifier.weight(1f)) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = item.caption ?: item.fileName,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                media.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.fileName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1)
                            item.caption?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Text(
                                "${item.mediaType} · ${item.createdAt.take(10)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatCost(value: Double): String {
    val intPart = value.toLong()
    val fracPart = ((value - intPart) * 100 + 0.5).toLong()
    return "$intPart.${fracPart.toString().padStart(2, '0')}"
}
