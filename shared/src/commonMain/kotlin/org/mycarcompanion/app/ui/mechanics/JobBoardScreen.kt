package org.mycarcompanion.app.ui.mechanics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import org.mycarcompanion.app.data.models.JobRequest
import org.mycarcompanion.app.data.models.Vehicle
import org.mycarcompanion.app.platform.CommonParcelable
import org.mycarcompanion.app.platform.scaffoldContentWindowInsets
import org.mycarcompanion.app.platform.topBarWindowInsets
import org.mycarcompanion.app.ui.messaging.MessagingScreen

/**
 * Owners post work they need done; verified mechanics browse and start a chat.
 * One screen, two modes — the card is the same, only the action differs.
 */
data class JobBoardScreen(val asMechanic: Boolean = false) : Screen, CommonParcelable {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: JobBoardScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        var showForm by remember { mutableStateOf(false) }

        LaunchedEffect(asMechanic) { model.load(asMechanic) }

        Scaffold(
            contentWindowInsets = scaffoldContentWindowInsets(),
            topBar = {
                TopAppBar(
                    title = { Text(if (asMechanic) "Job Board" else "My Requests") },
                    navigationIcon = {
                        OutlinedButton(
                            onClick = { navigator.pop() },
                            modifier = Modifier.padding(start = 4.dp),
                        ) { Text("Back") }
                    },
                    windowInsets = topBarWindowInsets(),
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .imePadding(),
            ) {
                if (!asMechanic) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (showForm) {
                        PostRequestForm(
                            vehicles = state.vehicles,
                            isPosting = state.isPosting,
                            error = state.postError,
                            onCancel = { showForm = false },
                            onPost = { vehicle, title, description, city, stateCode ->
                                model.post(vehicle, title, description, city, stateCode)
                                showForm = false
                            },
                        )
                    } else {
                        Button(
                            onClick = { showForm = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Post a request") }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                when {
                    state.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                    state.error != null -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { model.load(asMechanic) }) { Text("Retry") }
                        }
                    }
                    state.requests.isEmpty() -> {
                        Text(
                            text = if (asMechanic) "No open requests right now."
                            else "You haven't posted any requests yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 48.dp),
                        )
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            items(state.requests, key = { it.id }) { request ->
                                JobRequestCard(
                                    request = request,
                                    asMechanic = asMechanic,
                                    onMessageOwner = {
                                        navigator.push(MessagingScreen(recipientId = request.ownerId))
                                    },
                                    onClose = { model.close(request.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobRequestCard(
    request: JobRequest,
    asMechanic: Boolean,
    onMessageOwner: () -> Unit,
    onClose: () -> Unit,
) {
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
                    text = request.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (request.status == "closed") {
                    Text(
                        "Closed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = request.vehicleLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            request.description?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val location = listOfNotNull(request.city, request.state).joinToString(", ")
                Text(
                    text = location.ifBlank { "Location not given" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = request.createdAt.take(10),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (asMechanic) {
                Button(onClick = onMessageOwner, modifier = Modifier.fillMaxWidth()) {
                    Text("Message owner")
                }
            } else if (request.status == "open") {
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text("Close request")
                }
            }
        }
    }
}

@Composable
private fun PostRequestForm(
    vehicles: List<Vehicle>,
    isPosting: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onPost: (Vehicle, String, String, String, String) -> Unit,
) {
    var selectedVehicleId by remember(vehicles) { mutableStateOf(vehicles.firstOrNull()?.id) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var stateCode by remember { mutableStateOf("") }

    if (vehicles.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Add a vehicle first, then you can post a request.")
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Which vehicle?", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vehicles, key = { it.id }) { vehicle ->
                    FilterChip(
                        selected = vehicle.id == selectedVehicleId,
                        onClick = { selectedVehicleId = vehicle.id },
                        label = { Text("${vehicle.year} ${vehicle.make} ${vehicle.model}") },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("What do you need?") },
                placeholder = { Text("Brakes squealing at low speed") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Details (optional)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("City") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = stateCode,
                    onValueChange = { stateCode = it },
                    label = { Text("State") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Mechanics see the vehicle, the problem and the city — never your name, " +
                    "address or contact details. They reach you through in-app chat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        val vehicle = vehicles.first { it.id == selectedVehicleId }
                        onPost(vehicle, title, description, city, stateCode)
                    },
                    enabled = title.isNotBlank() && selectedVehicleId != null && !isPosting,
                    modifier = Modifier.weight(1f),
                ) { Text("Post") }
            }
        }
    }
}
