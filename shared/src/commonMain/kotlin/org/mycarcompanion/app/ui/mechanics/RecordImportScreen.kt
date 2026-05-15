package org.mycarcompanion.app.ui.mechanics

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.mycarcompanion.app.data.models.ImportTab
import org.mycarcompanion.app.data.models.ImportedRecord
import org.mycarcompanion.app.data.models.MechanicJob
import org.mycarcompanion.app.platform.CommonParcelable
import org.mycarcompanion.app.platform.rememberBinaryFilePickerLauncher
import org.mycarcompanion.app.platform.rememberTextFilePickerLauncher
import org.mycarcompanion.app.platform.scaffoldContentWindowInsets
import org.mycarcompanion.app.platform.topBarWindowInsets

data class RecordImportScreen(val job: MechanicJob) : Screen, CommonParcelable {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: RecordImportScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(state.error) {
            state.error?.let { snackbarHostState.showSnackbar(it); model.clearError() }
        }

        val csvPicker = rememberTextFilePickerLauncher { _, content ->
            model.onCsvContent(content)
        }
        val imagePicker = rememberBinaryFilePickerLauncher { _, base64, mimeType ->
            model.onInvoiceImage(base64, mimeType, job.id)
        }

        Scaffold(
            contentWindowInsets = scaffoldContentWindowInsets(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Import Records") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    windowInsets = topBarWindowInsets(),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                TabRow(selectedTabIndex = state.activeTab.ordinal) {
                    Tab(
                        selected = state.activeTab == ImportTab.CSV,
                        onClick = { model.selectTab(ImportTab.CSV) },
                        text = { Text("CSV Import") },
                    )
                    Tab(
                        selected = state.activeTab == ImportTab.AI_SCAN,
                        onClick = { model.selectTab(ImportTab.AI_SCAN) },
                        text = { Text("AI Scan") },
                    )
                }

                when {
                    state.saveSuccess -> SuccessBanner(count = state.savedCount, onDone = { navigator.pop() })
                    state.activeTab == ImportTab.CSV -> CsvTab(
                        state = state,
                        onPickFile = csvPicker::launch,
                        onImport = { model.importRecords(job.id, job.clientEmail) },
                    )
                    else -> AiScanTab(
                        state = state,
                        onPickImage = imagePicker::launch,
                        onImport = { model.importRecords(job.id, job.clientEmail) },
                    )
                }
            }
        }
    }
}

// ── CSV Tab ───────────────────────────────────────────────────────────────────

@Composable
private fun CsvTab(
    state: RecordImportState,
    onPickFile: () -> Unit,
    onImport: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            InfoCard(
                title = "How to use CSV import",
                body = "Create a spreadsheet with these columns in order, then export as CSV and upload:\n\n" +
                    "date · category · description · mileage · cost · notes\n\n" +
                    "Date format: YYYY-MM-DD\n" +
                    "Categories: Oil Change, Tire Rotation, Brake Service, Air Filter, Cabin Filter, " +
                    "Transmission Service, Coolant Flush, Battery Replacement, Wiper Blades, " +
                    "Spark Plugs, Alignment, Suspension, Registration, Inspection, Smog Check, Body Work, Other",
            )
        }

        item {
            FilledTonalButton(
                onClick = onPickFile,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isParsing && !state.isSaving,
            ) {
                Text("Pick CSV File")
            }
        }

        when {
            state.isParsing -> item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }
            }
            state.parsedRecords.isNotEmpty() -> {
                val valid = state.parsedRecords.count { it.isValid }
                val invalid = state.parsedRecords.size - valid
                item { RecordCountBadge(valid = valid, invalid = invalid) }
                items(state.parsedRecords) { record -> ImportedRecordCard(record) }
                item {
                    Button(
                        onClick = onImport,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = valid > 0 && !state.isSaving,
                    ) {
                        if (state.isSaving) CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        else Text("Import $valid Record${if (valid != 1) "s" else ""}")
                    }
                }
            }
        }
    }
}

// ── AI Scan Tab ───────────────────────────────────────────────────────────────

@Composable
private fun AiScanTab(
    state: RecordImportState,
    onPickImage: () -> Unit,
    onImport: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            InfoCard(
                title = "AI Invoice Scan",
                body = "Take a photo or upload an image of any service invoice, receipt, or work order. " +
                    "AI will automatically extract the service records and fill them in for you.\n\n" +
                    "Supported: JPEG, PNG, WebP, GIF, PDF",
            )
        }

        item {
            FilledTonalButton(
                onClick = onPickImage,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isScanning && !state.isSaving,
            ) {
                Text("Pick Invoice Image or PDF")
            }
        }

        when {
            state.isScanning -> item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Analyzing invoice...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.parsedRecords.isNotEmpty() -> {
                val valid = state.parsedRecords.count { it.isValid }
                val invalid = state.parsedRecords.size - valid
                item { RecordCountBadge(valid = valid, invalid = invalid) }
                items(state.parsedRecords) { record -> ImportedRecordCard(record) }
                item {
                    Button(
                        onClick = onImport,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = valid > 0 && !state.isSaving,
                    ) {
                        if (state.isSaving) CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        else Text("Import $valid Record${if (valid != 1) "s" else ""}")
                    }
                }
                if (state.parsedRecords.isNotEmpty()) {
                    item {
                        OutlinedButton(
                            onClick = onPickImage,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Scan Another Invoice") }
                    }
                }
            }
        }
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun RecordCountBadge(valid: Int, invalid: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                "$valid valid",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
        if (invalid > 0) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "$invalid skipped",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun ImportedRecordCard(record: ImportedRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (record.isValid) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(record.category, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(record.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (record.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(record.description, style = MaterialTheme.typography.bodySmall)
            }
            if (record.mileage > 0 || record.cost != null) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (record.mileage > 0) Text("${record.mileage} mi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    record.cost?.let { Text("$${formatCost(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            record.validationError?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatCost(value: Double): String {
    val intPart = value.toLong()
    val fracPart = ((value - intPart) * 100 + 0.5).toLong()
    return "$intPart.${fracPart.toString().padStart(2, '0')}"
}

@Composable
private fun SuccessBanner(count: Int, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Successfully imported $count record${if (count != 1) "s" else ""}!",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "These records are now visible in the job's service log and in your client's vehicle history.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.width(200.dp)) { Text("Done") }
    }
}
