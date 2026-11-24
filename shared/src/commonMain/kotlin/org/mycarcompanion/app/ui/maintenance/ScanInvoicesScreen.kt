package org.mycarcompanion.app.ui.maintenance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.mycarcompanion.app.platform.CommonParcelable
import org.mycarcompanion.app.platform.rememberBinaryFilePickerLauncher
import org.mycarcompanion.app.platform.rememberCameraPhotoLauncher
import org.mycarcompanion.app.ui.formatUsd
import org.mycarcompanion.app.platform.topBarWindowInsets

/** Owner scans old invoices / service records; AI pulls out the records to review and save. */
data class ScanInvoicesScreen(val vehicleId: String) : Screen, CommonParcelable {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: ScanInvoicesScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val filePicker = rememberBinaryFilePickerLauncher { name, base64, mime -> model.scan(vehicleId, name, base64, mime) }
        val camera = rememberCameraPhotoLauncher { base64, mime -> model.scan(vehicleId, "invoice-photo.jpg", base64, mime) }

        LaunchedEffect(vehicleId) { model.load(vehicleId) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Scan invoices") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                item {
                    Text(
                        "Add an old invoice, work order or service record as a PDF or photo. We'll pull out each service so you can check it before saving. The file is kept privately with the records as proof.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = filePicker::launch, enabled = !state.isScanning && !state.isSaving) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("PDF or photo")
                        }
                        OutlinedButton(onClick = camera::launch, enabled = !state.isScanning && !state.isSaving) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Take photo")
                        }
                    }
                    Text(
                        "Free accounts get 10 scans a month. Premium is unlimited.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (state.isScanning) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Reading your invoice…")
                        }
                    }
                }
                state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                state.savedCount?.let { n ->
                    item {
                        Text(
                            "Saved $n record${if (n == 1) "" else "s"}. Scan another, or go back to see them.",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                if (state.records.isNotEmpty()) {
                    item { Text("Found ${state.records.size} — untick any that are wrong", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                    itemsIndexed(state.records) { i, r ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = state.keep.getOrElse(i) { false },
                                    onCheckedChange = { model.toggle(i) },
                                    enabled = r.isValid,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${r.category} · ${r.date}", style = MaterialTheme.typography.titleSmall)
                                    Text(r.description, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        listOfNotNull(
                                            r.mileage.takeIf { it > 0 }?.let { "$it mi" },
                                            r.cost?.let(::formatUsd),
                                            r.notes,
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    state.newOdometer?.let { odo ->
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = state.updateOdometer, onCheckedChange = model::setUpdateOdometer)
                                Text("Update the car's mileage to $odo mi")
                            }
                        }
                    }
                    state.vinToFill?.let { vin ->
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = state.fillVin, onCheckedChange = model::setFillVin)
                                Text("Save VIN $vin to this car")
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = { model.save(vehicleId) },
                            enabled = !state.isSaving && state.keptCount > 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text("Save ${state.keptCount} record${if (state.keptCount == 1) "" else "s"}")
                        }
                    }
                }
            }
        }
    }
}
