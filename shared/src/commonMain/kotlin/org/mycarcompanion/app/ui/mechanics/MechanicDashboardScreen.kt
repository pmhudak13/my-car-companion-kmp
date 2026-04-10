package org.mycarcompanion.app.ui.mechanics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.mycarcompanion.app.data.models.AuthState
import org.mycarcompanion.app.data.models.MechanicAssignment
import org.mycarcompanion.app.ui.auth.LoginScreen
import org.mycarcompanion.app.ui.home.HomeScreen
import org.mycarcompanion.app.ui.home.HomeScreenModel
import org.mycarcompanion.app.ui.messaging.MessagingScreen

@OptIn(ExperimentalMaterial3Api::class)
class MechanicDashboardScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: MechanicDashboardScreenModel = koinScreenModel()
        val homeModel: HomeScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val authState by homeModel.authState.collectAsState()
        val currentUser = (authState as? org.mycarcompanion.app.data.models.AuthState.Authenticated)?.user

        LaunchedEffect(authState) {
            if (authState is AuthState.Unauthenticated) {
                navigator.replaceAll(LoginScreen())
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(state.profile?.shopName ?: "My Dashboard") },
                    actions = {
                        IconButton(onClick = { navigator.push(MessagingScreen()) }) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Messages",
                            )
                        }
                        IconButton(onClick = { navigator.push(MechanicSetupScreen()) }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Profile",
                            )
                        }
                        if (currentUser?.isAdmin == true) {
                            TextButton(onClick = { navigator.replace(HomeScreen()) }) {
                                Text("Individual View", color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        TextButton(onClick = homeModel::signOut) {
                            Text("Sign Out", color = MaterialTheme.colorScheme.error)
                        }
                    },
                )
            },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                when {
                    state.isLoading && state.assignments.isEmpty() -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.error != null -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = state.error ?: "Unknown error",
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = model::refresh) {
                                Text("Retry")
                            }
                        }
                    }
                    state.assignments.isEmpty() -> {
                        Text(
                            text = "No active jobs right now.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(48.dp),
                        )
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            items(state.assignments, key = { it.id }) { assignment ->
                                AssignmentCard(
                                    assignment = assignment,
                                    isCompleting = state.completingId == assignment.id,
                                    onComplete = { model.completeJob(assignment.id) },
                                    onViewVehicle = { navigator.push(MechanicVehicleViewScreen(assignment.vehicleId, assignment.id)) },
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
private fun AssignmentCard(
    assignment: MechanicAssignment,
    isCompleting: Boolean,
    onComplete: () -> Unit,
    onViewVehicle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onViewVehicle,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = assignment.notes ?: "No notes",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Assigned ${assignment.assignedAt.take(10)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (isCompleting) {
                CircularProgressIndicator()
            } else {
                FilledTonalButton(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Complete Job")
                }
            }
        }
    }
}
