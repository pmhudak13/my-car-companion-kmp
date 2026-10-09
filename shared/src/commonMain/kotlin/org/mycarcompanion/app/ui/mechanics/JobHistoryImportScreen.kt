package org.mycarcompanion.app.ui.mechanics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.mycarcompanion.app.data.repository.JobLineItemRepository
import org.mycarcompanion.app.data.repository.MechanicJobRepository
import org.mycarcompanion.app.data.repository.ProfileRepository
import org.mycarcompanion.app.platform.rememberTextFilePickerLauncher
import org.mycarcompanion.app.platform.scaffoldContentWindowInsets
import org.mycarcompanion.app.platform.topBarWindowInsets
import org.mycarcompanion.app.ui.formatUsd

/**
 * A new mechanic brings in their past jobs from a spreadsheet. They land as completed jobs with real
 * line items, so copying past jobs, "last charged" prices, and the labor guide work from day one.
 */
// ponytail: plain remember state like LaborGuideScreen; parse + one RPC, nothing worth a ScreenModel
class JobHistoryImportScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository: JobLineItemRepository = koinInject()
        val jobRepository: MechanicJobRepository = koinInject()
        val profileRepository: ProfileRepository = koinInject()
        val scope = rememberCoroutineScope()

        var parsed by remember { mutableStateOf<HistoryParse?>(null) }
        var saving by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var importedCount by remember { mutableStateOf<Int?>(null) }
        var customersWithEmail by remember { mutableStateOf(0) }
        var inviting by remember { mutableStateOf(false) }
        var inviteResult by remember { mutableStateOf<String?>(null) }

        val picker = rememberTextFilePickerLauncher { _, content ->
            error = null
            importedCount = null
            parsed = parseJobHistoryCsv(content)
        }

        fun startImport() {
            val jobs = parsed?.takeIf { it.errors.isEmpty() && it.jobs.isNotEmpty() }?.jobs ?: return
            scope.launch {
                saving = true; error = null
                repository.importJobHistory(jobs)
                    .onSuccess {
                        importedCount = it
                        customersWithEmail = jobs.mapNotNull { j -> j.clientEmail?.lowercase() }.distinct().size
                        parsed = null
                    }
                    .onFailure { error = "Import failed, nothing was saved: ${it.message}" }
                saving = false
            }
        }

        fun inviteAll() {
            scope.launch {
                inviting = true
                val shop = profileRepository.getMyMechanicProfile().getOrNull()?.shopName ?: "Your Mechanic"
                inviteResult = jobRepository.inviteUninvitedCustomers(shop).fold(
                    onSuccess = { "Sent $it invite${if (it == 1) "" else "s"}." },
                    onFailure = { "Invites failed: ${it.message}" },
                )
                inviting = false
            }
        }

        Scaffold(
            contentWindowInsets = scaffoldContentWindowInsets(),
            topBar = {
                TopAppBar(
                    title = { Text("Import Past Jobs") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    windowInsets = topBarWindowInsets(),
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { HowToCard() }
                item {
                    FilledTonalButton(onClick = picker::launch, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                        Text("Pick CSV File")
                    }
                }
                importedCount?.let { n ->
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Imported $n past job${if (n == 1) "" else "s"}", fontWeight = FontWeight.Bold)
                                Text(
                                    "They're in My Jobs as completed. On any estimate, use \"Copy past job\", and you'll see what you charged last time as you type.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (customersWithEmail > 0) {
                                    Text(
                                        "Invite your $customersWithEmail customer${if (customersWithEmail == 1) "" else "s"} with an email. " +
                                            "When they join with that email, their service history is already on their car.",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    when {
                                        inviting -> CircularProgressIndicator()
                                        inviteResult != null -> Text(inviteResult!!, fontWeight = FontWeight.Bold)
                                        else -> FilledTonalButton(onClick = ::inviteAll) { Text("Email Invites to Customers") }
                                    }
                                }
                                Button(onClick = { navigator.pop() }) { Text("Done") }
                            }
                        }
                    }
                }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                parsed?.let { p -> previewItems(p, saving, ::startImport) }
            }
        }
    }
}

@Composable
private fun HowToCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bring in your past jobs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Make a spreadsheet with one row per part, labor, or fee line, in these columns, then save it as CSV:",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(historyCsvColumns.joinToString(" · "), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Optional: ${historyCsvOptionalColumns.joinToString(" · ")}. Add the customer's email so you can invite them " +
                    "and their history shows on their car when they join.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Rows with the same date, customer, car, and job become one job. Type is Labor, Part, or Fee " +
                    "(a Fee can be negative for a discount). For labor, Qty is hours and Price is your hourly rate. " +
                    "Dates can be 2025-03-04 or 3/4/2025. Up to $MAX_HISTORY_JOBS jobs per file.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val uriHandler = LocalUriHandler.current
            TextButton(onClick = { uriHandler.openUri("https://www.mycarcompanion.org/import-records") }) {
                Text("Step-by-step guide and template")
            }
            Text("Example:", style = MaterialTheme.typography.bodySmall)
            SelectionContainer {
                Text(historyCsvExample, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.previewItems(p: HistoryParse, saving: Boolean, onImport: () -> Unit) {
    if (p.errors.isNotEmpty()) {
        item {
            Text(
                "Fix these rows and pick the file again. Nothing is imported until every row is valid.",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
        }
        items(p.errors.take(50)) { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (p.errors.size > 50) item { Text("…and ${p.errors.size - 50} more", style = MaterialTheme.typography.bodySmall) }
        return
    }
    if (p.jobs.isEmpty()) {
        item { Text("No rows found. Check the file matches the columns above.", color = MaterialTheme.colorScheme.error) }
        return
    }
    item {
        Text(
            "${p.jobs.size} jobs · ${p.lineCount} lines · ${formatUsd(p.total)} before tax",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
    }
    item {
        Button(onClick = onImport, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
            if (saving) CircularProgressIndicator(Modifier.padding(2.dp)) else Text("Import ${p.jobs.size} jobs")
        }
    }
    items(p.jobs.take(100)) { job ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("${job.date} · ${job.year} ${job.make} ${job.model}", fontWeight = FontWeight.Bold)
                Text(listOfNotNull(job.clientName, job.description).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                job.lines.forEach { l ->
                    Text(
                        "${l.kind.replaceFirstChar { it.uppercase() }}: ${l.description} · ${formatQty(l.quantity)} × ${formatUsd(l.unitPrice)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (p.jobs.size > 100) item { Text("…and ${p.jobs.size - 100} more jobs", style = MaterialTheme.typography.bodySmall) }
}
