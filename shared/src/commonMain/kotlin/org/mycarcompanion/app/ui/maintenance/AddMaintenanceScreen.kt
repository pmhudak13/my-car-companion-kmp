package org.mycarcompanion.app.ui.maintenance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.mycarcompanion.app.data.models.maintenanceCategories
import org.mycarcompanion.app.platform.CommonParcelable
import org.mycarcompanion.app.platform.rememberImagePickerLauncher

data class AddMaintenanceScreen(
    val vehicleId: String,
    val requiresApproval: Boolean = false,
) : Screen, CommonParcelable {

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: AddMaintenanceScreenModel = koinScreenModel()
        val state by model.state.collectAsState()

        val launchImagePicker = rememberImagePickerLauncher { bytes ->
            if (bytes != null) model.addPhoto(bytes)
        }

        LaunchedEffect(state.saved) {
            if (state.saved) navigator.pop()
        }

        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Add Maintenance", style = MaterialTheme.typography.headlineMedium)
                    TextButton(onClick = { navigator.pop() }) { Text("Cancel") }
                }

                if (requiresApproval) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This record will be sent to the vehicle owner for approval.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                state.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text("Category *", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    maintenanceCategories.forEach { category ->
                        FilterChip(
                            selected = state.form.category == category,
                            onClick = { model.updateForm(state.form.copy(category = category)) },
                            label = { Text(category, style = MaterialTheme.typography.bodySmall) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.form.description,
                    onValueChange = { model.updateForm(state.form.copy(description = it)) },
                    label = { Text("Description *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.form.date,
                    onValueChange = { model.updateForm(state.form.copy(date = it)) },
                    label = { Text("Date * (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.form.mileage,
                    onValueChange = { model.updateForm(state.form.copy(mileage = it)) },
                    label = { Text("Mileage *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.form.cost,
                    onValueChange = { model.updateForm(state.form.copy(cost = it)) },
                    label = { Text("Cost") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.form.notes,
                    onValueChange = { model.updateForm(state.form.copy(notes = it)) },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Photo section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Photos", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(
                        onClick = launchImagePicker,
                        enabled = !state.isSaving && !state.isUploadingPhotos,
                    ) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(" Add Photo", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (state.form.photoUris.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        itemsIndexed(state.form.photoUris) { index, _ ->
                            PhotoThumbnailPending(
                                index = index,
                                onRemove = { model.removePhoto(index) },
                            )
                        }
                    }
                    if (state.isUploadingPhotos) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Uploading photos...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { model.save(vehicleId, requiresApproval) },
                    enabled = !state.isSaving && !state.isUploadingPhotos,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSaving || state.isUploadingPhotos) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(if (requiresApproval) "Submit for Owner Approval" else "Save Maintenance Record")
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoThumbnailPending(index: Int, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${index + 1}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove photo",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
