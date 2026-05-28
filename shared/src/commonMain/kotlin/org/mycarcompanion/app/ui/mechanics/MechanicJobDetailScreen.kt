package org.mycarcompanion.app.ui.mechanics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import org.mycarcompanion.app.data.models.MechanicJob
import org.mycarcompanion.app.data.models.MechanicJobIssue
import org.mycarcompanion.app.data.models.MechanicJobLog
import org.mycarcompanion.app.data.models.MechanicJobMedia
import org.mycarcompanion.app.data.models.maintenanceCategories
import org.mycarcompanion.app.platform.CommonParcelable
import org.mycarcompanion.app.platform.scaffoldContentWindowInsets
import org.mycarcompanion.app.platform.topBarWindowInsets
import org.mycarcompanion.app.platform.rememberBinaryFilePickerLauncher

data class MechanicJobDetailScreen(val jobId: String) : Screen, CommonParcelable {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: MechanicJobDetailScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        val mediaPicker = rememberBinaryFilePickerLauncher { fileName, base64, mimeType ->
            model.uploadMedia(jobId, fileName, base64, mimeType)
        }

        LaunchedEffect(jobId) { model.load(jobId) }
        LaunchedEffect(state.completed) { if (state.completed) navigator.pop() }
        LaunchedEffect(state.inviteMessage) {
            state.inviteMessage?.let {
                snackbarHostState.showSnackbar(it)
                model.clearInviteMessage()
            }
        }
        LaunchedEffect(state.error) {
            state.error?.let {
                snackbarHostState.showSnackbar(it)
                model.clearError()
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
                        // Job info card
                        item { JobInfoCard(job) }

                        // Progress section (always visible while job is open or recently completed)
                        item {
                            ProgressSection(
                                percent = state.progressPercent,
                                isSaving = state.isSavingProgress,
                                onValueChange = model::setProgressPercent,
                                onSave = model::saveProgress,
                            )
                        }

                        // Invite card
                        if (!job.clientEmail.isNullOrBlank()) {
                            item {
                                InviteCard(
                                    job = job,
                                    isSending = state.isSendingInvite,
                                    onSend = model::sendInvite,
                                )
                            }
                        }

                        // Complete job button
                        if (job.status == "open") {
                            item {
                                CompleteJobCard(
                                    canComplete = state.canComplete,
                                    pendingIssueCount = state.pendingIssueCount,
                                    isCompleting = state.isCompleting,
                                    onComplete = { model.completeJob(job.id) },
                                )
                            }
                        }

                        // Issues section
                        item {
                            IssuesSectionHeader(
                                issueCount = state.issues.size,
                                pendingCount = state.pendingIssueCount,
                                onFlagIssue = model::showIssueForm,
                            )
                        }
                        if (state.issues.isEmpty()) {
                            item {
                                Text(
                                    "No issues flagged.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(state.issues, key = { it.id }) { issue ->
                                IssueCard(
                                    issue = issue,
                                    onDelete = { model.deleteIssue(issue.id) },
                                )
                            }
                        }

                        // Media section
                        item {
                            MediaSectionHeader(
                                count = state.mediaItems.size,
                                isUploading = state.isUploadingMedia,
                                onUpload = { mediaPicker.launch() },
                            )
                        }
                        state.mediaError?.let { error ->
                            item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                        if (state.mediaItems.isNotEmpty()) {
                            item {
                                MediaGrid(
                                    items = state.mediaItems,
                                    onDelete = model::deleteMedia,
                                )
                            }
                        }

                        // Service logs
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalIconButton(onClick = { navigator.push(RecordImportScreen(job)) }) {
                                        Icon(Icons.Default.Upload, contentDescription = "Import Records")
                                    }
                                    TextButton(onClick = model::showAddLog) { Text("+ Add Log") }
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

            // Add log sheet
            if (state.showAddLog) {
                ModalBottomSheet(onDismissRequest = model::hideAddLog, sheetState = bottomSheetState) {
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

            // Edit log sheet
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

            // Flag issue sheet
            if (state.showIssueForm) {
                ModalBottomSheet(
                    onDismissRequest = model::hideIssueForm,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                ) {
                    IssueFormSheet(
                        form = state.issueForm,
                        isSaving = state.isSavingIssue,
                        error = state.issueError,
                        onFormUpdate = model::updateIssueForm,
                        onSave = { model.saveIssue(jobId) },
                        onCancel = model::hideIssueForm,
                    )
                }
            }
        }
    }
}

// ── Job Info Card ─────────────────────────────────────────────────────────────

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
                Text(job.clientName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            Text("${job.vehicleYear} ${job.vehicleMake} ${job.vehicleModel}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            job.vehicleColor?.let { Text("Color: $it", style = MaterialTheme.typography.bodySmall) }
            job.vehicleLicensePlate?.let { Text("Plate: $it", style = MaterialTheme.typography.bodySmall) }
            job.vehicleVin?.let { Text("VIN: $it", style = MaterialTheme.typography.bodySmall) }
            job.description?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Created ${job.createdAt.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            job.completedAt?.let {
                Text("Completed ${it.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ── Progress Section ──────────────────────────────────────────────────────────

@Composable
private fun ProgressSection(
    percent: Int,
    isSaving: Boolean,
    onValueChange: (Int) -> Unit,
    onSave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Job Progress", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("$percent%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = percent.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = 0f..100f,
                steps = 19,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    FilledTonalButton(onClick = onSave) { Text("Save Progress") }
                }
            }
        }
    }
}

// ── Complete Job Card ─────────────────────────────────────────────────────────

@Composable
private fun CompleteJobCard(
    canComplete: Boolean,
    pendingIssueCount: Int,
    isCompleting: Boolean,
    onComplete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (pendingIssueCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "$pendingIssueCount pending issue${if (pendingIssueCount != 1) "s" else ""} — waiting for owner approval",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (isCompleting) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = onComplete,
                    enabled = canComplete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(if (canComplete) "Mark Job Complete" else "Cannot Complete — Pending Issues")
                }
            }
        }
    }
}

// ── Issues ────────────────────────────────────────────────────────────────────

@Composable
private fun IssuesSectionHeader(issueCount: Int, pendingCount: Int, onFlagIssue: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Potential Issues ($issueCount)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (pendingCount > 0) {
                Text(
                    "$pendingCount awaiting owner approval",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        FilledTonalButton(onClick = onFlagIssue) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Flag Issue")
        }
    }
}

@Composable
private fun IssueCard(issue: MechanicJobIssue, onDelete: () -> Unit) {
    val (containerColor, labelColor, label) = when (issue.status) {
        "approved" -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Approved",
        )
        "declined" -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Declined",
        )
        else -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "Pending Approval",
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(issue.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(label, style = MaterialTheme.typography.labelSmall, color = labelColor)
            }
            issue.description?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            issue.estimatedCost?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Est. cost: $${formatCost(it)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
            issue.ownerResponse?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Owner: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (issue.status == "pending") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete) {
                        Text("Remove", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// ── Media Section ─────────────────────────────────────────────────────────────

@Composable
private fun MediaSectionHeader(count: Int, isUploading: Boolean, onUpload: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Photos & Videos ($count)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (isUploading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            FilledTonalButton(onClick = onUpload) {
                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Upload")
            }
        }
    }
}

@Composable
private fun MediaGrid(
    items: List<MechanicJobMedia>,
    onDelete: (MechanicJobMedia) -> Unit,
) {
    var showThumbnails by rememberSaveable { mutableStateOf(true) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showThumbnails = true }) {
                Text(
                    "Thumbnails",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (showThumbnails) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("·", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { showThumbnails = false }) {
                Text(
                    "File List",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (!showThumbnails) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (showThumbnails) {
            // 3-column grid, fixed height so it doesn't fight LazyColumn
            val rows = (items.size + 2) / 3
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(rows) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (col in 0..2) {
                            val idx = row * 3 + col
                            if (idx < items.size) {
                                MediaThumbnailCard(
                                    media = items[idx],
                                    onDelete = { onDelete(items[idx]) },
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { media ->
                    MediaFileRow(media = media, onDelete = { onDelete(media) })
                }
            }
        }
    }
}

@Composable
private fun MediaThumbnailCard(
    media: MechanicJobMedia,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val url = "${org.mycarcompanion.app.data.supabase.SupabaseConfig.url}/storage/v1/object/public/mechanic-job-media/${media.storagePath}"
    Card(modifier = modifier) {
        Box {
            AsyncImage(
                model = url,
                contentDescription = media.caption ?: media.fileName,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Text("✕", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        media.caption?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(4.dp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MediaFileRow(media: MechanicJobMedia, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(media.fileName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1)
                media.caption?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Text(
                    "${media.mediaType} · ${media.createdAt.take(10)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ── Invite Card ───────────────────────────────────────────────────────────────

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
                Text("Invite to App", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(job.clientEmail ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                if (job.inviteSent) {
                    Text("Invite sent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isSending) CircularProgressIndicator()
            else OutlinedButton(onClick = onSend) { Text(if (job.inviteSent) "Resend" else "Send Invite") }
        }
    }
}

// ── Service Log Cards / Sheet ─────────────────────────────────────────────────

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
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (log.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(log.description, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row {
                    if (log.mileage > 0) Text("${log.mileage} mi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    log.cost?.let { cost ->
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("$${formatCost(cost)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
            log.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
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
        modifier = Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = 32.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Category *", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            maintenanceCategories.forEach { category ->
                FilterChip(
                    selected = form.category == category,
                    onClick = { onFormUpdate(form.copy(category = category)) },
                    label = { Text(category, style = MaterialTheme.typography.bodySmall) },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = form.description, onValueChange = { onFormUpdate(form.copy(description = it)) }, label = { Text("Description *") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = form.date, onValueChange = { onFormUpdate(form.copy(date = it)) }, label = { Text("Date * (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = form.mileage, onValueChange = { onFormUpdate(form.copy(mileage = it)) }, label = { Text("Mileage *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = form.cost, onValueChange = { onFormUpdate(form.copy(cost = it)) }, label = { Text("Cost") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = form.notes, onValueChange = { onFormUpdate(form.copy(notes = it)) }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) {
            if (isSaving) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
            else Text("Save Log")
        }
    }
}

// ── Issue Form Sheet ──────────────────────────────────────────────────────────

@Composable
private fun IssueFormSheet(
    form: IssueForm,
    isSaving: Boolean,
    error: String?,
    onFormUpdate: (IssueForm) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = 32.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Flag Potential Issue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Owner will be notified and must approve before the job can be marked complete.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = form.title, onValueChange = { onFormUpdate(form.copy(title = it)) }, label = { Text("Issue Title *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = form.description, onValueChange = { onFormUpdate(form.copy(description = it)) }, label = { Text("Description") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = form.estimatedCost, onValueChange = { onFormUpdate(form.copy(estimatedCost = it)) }, label = { Text("Estimated Cost") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Text("$") })
        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) {
            if (isSaving) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
            else Text("Flag Issue & Notify Owner")
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatCost(value: Double): String {
    val intPart = value.toLong()
    val fracPart = ((value - intPart) * 100 + 0.5).toLong()
    return "$intPart.${fracPart.toString().padStart(2, '0')}"
}
