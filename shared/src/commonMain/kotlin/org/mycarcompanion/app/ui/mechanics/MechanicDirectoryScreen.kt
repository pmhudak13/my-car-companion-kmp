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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.mycarcompanion.app.data.models.MechanicProfile
import org.mycarcompanion.app.data.models.shopTypeLabels
import org.mycarcompanion.app.platform.CommonParcelable
import org.mycarcompanion.app.ui.messaging.MessagingScreen

data class MechanicDirectoryScreen(val vehicleId: String? = null) : Screen, CommonParcelable {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: MechanicDirectoryScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(state.assignSuccess) {
            if (state.assignSuccess) {
                snackbarHostState.showSnackbar("Mechanic assigned successfully!")
                model.clearAssignResult()
                navigator.pop()
            }
        }

        LaunchedEffect(state.assignError) {
            state.assignError?.let { err ->
                snackbarHostState.showSnackbar(err)
                model.clearAssignResult()
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Find a Mechanic",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = { navigator.pop() }) { Text("Back") }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when {
                    state.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    state.error != null -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = model::loadMechanics) { Text("Retry") }
                        }
                    }
                    state.mechanics.isEmpty() -> {
                        Text(
                            "No verified mechanics available yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(48.dp),
                        )
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            items(state.mechanics, key = { it.id }) { mechanic ->
                                MechanicCard(
                                    mechanic = mechanic,
                                    vehicleId = vehicleId,
                                    isAssigning = state.assigningMechanicId == mechanic.userId,
                                    onAssign = { vehicleId?.let { vid -> model.assignMechanic(vid, mechanic.userId) } },
                                    onMessage = { navigator.push(MessagingScreen(recipientId = mechanic.userId)) },
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
fun MechanicCard(
    mechanic: MechanicProfile,
    vehicleId: String? = null,
    isAssigning: Boolean = false,
    onAssign: () -> Unit = {},
    onMessage: () -> Unit = {},
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
                    text = mechanic.shopName ?: "Unnamed Shop",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = shopTypeLabels[mechanic.shopType] ?: mechanic.shopType,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            mechanic.bio?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val location = listOfNotNull(mechanic.city, mechanic.state).joinToString(", ")
                if (location.isNotBlank()) {
                    Text(location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row {
                    mechanic.rating?.let { rating ->
                        Text(
                            "${formatRating(rating)} ★",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    mechanic.yearsExperience?.let { years ->
                        if (years > 0) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "${years}yr exp",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            mechanic.hourlyRate?.let { rate ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "$${formatRate(rate)}/hr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onMessage,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Message", style = MaterialTheme.typography.labelMedium)
                }
                if (vehicleId != null) {
                    if (isAssigning) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Button(
                            onClick = onAssign,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Assign", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

private fun formatRating(value: Double): String {
    val intPart = value.toLong()
    val fracPart = ((value - intPart) * 10 + 0.5).toLong()
    return "$intPart.$fracPart"
}

private fun formatRate(value: Double): String {
    val intPart = value.toLong()
    val fracPart = ((value - intPart) * 100 + 0.5).toLong()
    return "$intPart.${fracPart.toString().padStart(2, '0')}"
}
