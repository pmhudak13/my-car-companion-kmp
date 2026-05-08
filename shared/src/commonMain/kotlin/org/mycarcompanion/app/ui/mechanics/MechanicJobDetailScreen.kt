package org.mycarcompanion.app.ui.mechanics

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.mycarcompanion.app.data.models.MechanicJob
import org.mycarcompanion.app.platform.topBarWindowInsets
import org.mycarcompanion.app.data.models.MechanicJobLog
import org.mycarcompanion.app.data.models.maintenanceCategories
import org.mycarcompanion.app.platform.CommonParcelable
import org.mycarcompanion.app.platform.scaffoldContentWindowInsets

data class MechanicJobDetailScreen(val jobId: String) : Screen, CommonParcelable {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: MechanicJobDetailScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        LaunchedEffect(jobId) { model.load(jobId) }
        LaunchedEffect(state.completed) { if (state.completed) navigator.pop() }
        LaunchedEffect(state.inviteMessage) {
            state.inviteMessage?.let {
                snackbarHostState.showSnackbar(it)
                model.clearInviteMessage()
            }
        }

        Scaffold(
            contentWindowInsets = scaffoldContentWindowInsets(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        val job = state.job
                        Text(if (job != null) "${job.vehicleYear} ${job.vehicleMake} ${job.vehicleModel}" else "Job")
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    windowInsets = topBarWindowInsets(),
                )
            },
            floatingActionButton = {
                if (state.job != null) {
                    FloatingActionButton(onClick = model::showAddLog) {
                        Icon(Icons.Default.Add, contentDescription = "Add Service Log")
                    }
                }
            },
        ) { paddingValues ->
            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.job == null -> Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) { Text(state.error ?: "Job not found", color = MaterialTheme.colorScheme.error) }

                else -> {
                    val job = state.job!!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.error?.let { error ->
                            item { Text(error, color = MaterialTheme.colorScheme.error) }
                        }

                        // Job info card
                        item { JobInfoCard(job) }

                        // Invite card (only if email provided)
                        if (!job.clientEmail.isNullOrBlank()) {
                            item {
                                InviteCard(
                                    job = job,
                                    isSending = state.isSendingInvite,
                                    onSend = model::sendInvite,
                                )
                            }
                        }

                        // Complete job button (only if open)
                        if (job.status == "open") {
                            item {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        if (state.isCompleting) {
                                            CircularProgressIndicator()
                                        } else {
                                            FilledTonalButton(
                                                onClick = { model.completeJob(job.id) },
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text("Mark Job Complete")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Service logs header
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Service Logs (${state.logs.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                TextButton(onClick = model::showAddLog) {
                                    Text("+ Add Log")
                                }
                            }
                        }

                        if (state.logs.isEmpty()) {
                            item {
                                Text(
                                    "No service logs yet. Tap + to add work done.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(state.logs, key = { it.id }) { log ->
                                JobLogCard(
                                    log = log,
                                    onEdit = { model.showEditLog(log) },
                                    onDelete = { model.deleteLog(log.id) },
                                )
                            }
                        }
                    }
                }
            }

            // Bottom sheet for adding a service log
            if (state.showAddLog) {
                ModalBottomSheet(
                    onDismissRequest = model::hideAddLog,
                    sheetState = bottomSheetState,
                ) {
                    JobLogSheet(
                        title = "Add Service Log",
                        form = state.logForm,
                        isSaving = state.isSavingLog,
                        error = state.logError,
                        onFormUpdate = model::updateLogForm,
                        onSave = { model.saveLog(jobId) },
                        onCancel = model::hideAddLog,
                    )
                }
            }

            // Bottom sheet for editing an existing service log
            if (state.editingLog != null) {
                ModalBottomSheet(
                    onDismissRequest = model::hideEditLog,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                ) {
                    JobLogSheet(
                        title = "Edit Service Log",
                        form = state.editLogForm,
                        isSaving = state.isUpdatingLog,
                        error = state.editLogError,
                        onFormUpdate = model::updateEditLogForm,
                        onSave = { model.saveEditLog(jobId) },
                        onCancel = model::hideEditLog,
                    )
                }
            }
        }
    }
}

@Composable
private fun JobInfoCard(job: MechanicJob) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = job.clientName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (job.status == "open") "Open" else "Completed",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (job.status == "open") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
            job.clientEmail?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${job.vehicleYear} ${job.vehicleMake} ${job.vehicleModel}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            job.vehicleColor?.let { Text("Color: $it", style = MaterialTheme.typography.bodySmall) }
            job.vehicleLicensePlate?.let { Text("Plate: $it", style = MaterialTheme.typography.bodySmall) }
            job.vehicleVin?.let { Text("VIN: $it", style = MaterialTheme.typography.bodySmall) }
            job.description?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Created ${job.createdAt.take(10)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            job.completedAt?.let {
                Text(
                    "Completed ${it.take(10)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun InviteCard(job: MechanicJob, isSending: Boolean, onSend: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Invite to App",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    job.clientEmail ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (job.inviteSent) {
                    Text(
                        "Invite sent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isSending) {
                CircularProgressIndicator()
            } else {
                OutlinedButton(onClick = onSend) {
                    Text(if (job.inviteSent) "Resend" else "Send Invite")
                }
            }
        }
    }
}

@Composable
private fun JobLogCard(log: MechanicJobLog, onEdit: () -> Unit, onDelete: () -> Unit) {
    val isEdited = log.updatedAt.isNotBlank() && log.updatedAt != log.createdAt
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(log.category, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(log.date.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (log.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(log.description, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row {
                    if (log.mileage > 0) {
                        Text("${log.mileage} mi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    log.cost?.let { cost ->
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("$${formatJobCost(cost)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
            log.notes?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JobLogSheet(
    title: String,
    form: org.mycarcompanion.app.data.models.MaintenanceFormData,
    isSaving: Boolean,
    error: String?,
    onFormUpdate: (org.mycarcompanion.app.data.models.MaintenanceFormData) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Text("Category *", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            maintenanceCategories.forEach { category ->
                FilterChip(
                    selected = form.category == category,
                    onClick = { onFormUpdate(form.copy(category = category)) },
                    label = { Text(category, style = MaterialTheme.typography.bodySmall) },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = form.description,
            onValueChange = { onFormUpdate(form.copy(description = it)) },
            label = { Text("Description *") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = form.date,
            onValueChange = { onFormUpdate(form.copy(date = it)) },
            label = { Text("Date * (YYYY-MM-DD)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = form.mileage,
            onValueChange = { onFormUpdate(form.copy(mileage = it)) },
            label = { Text("Mileage *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = form.cost,
            onValueChange = { onFormUpdate(form.copy(cost = it)) },
            label = { Text("Cost") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = form.notes,
            onValueChange = { onFormUpdate(form.copy(notes = it)) },
            label = { Text("Notes") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSave,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Save Log")
            }
        }
    }
}

private fun formatJobCost(value: Double): String {
    val intPart = value.toLong()
    val fracPart = ((value - intPart) * 100 + 0.5).toLong()
    return "$intPart.${fracPart.toString().padStart(2, '0')}"
}
