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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.mycarcompanion.app.data.models.AuthState
import org.mycarcompanion.app.data.models.MechanicAssignment
import org.mycarcompanion.app.data.models.MechanicJob
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
        val currentUser = (authState as? AuthState.Authenticated)?.user

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
                            Icon(Icons.Default.Email, contentDescription = "Messages")
                        }
                        IconButton(onClick = { navigator.push(MechanicSetupScreen()) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Profile")
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
            floatingActionButton = {
                if (state.selectedTab == MechanicDashboardTab.MY_JOBS) {
                    FloatingActionButton(onClick = { navigator.push(CreateMechanicJobScreen()) }) {
                        Icon(Icons.Default.Add, contentDescription = "Create Job")
                    }
                }
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                    Tab(
                        selected = state.selectedTab == MechanicDashboardTab.CLIENT_JOBS,
                        onClick = { model.selectTab(MechanicDashboardTab.CLIENT_JOBS) },
                        text = { Text("Client Jobs") },
                    )
                    Tab(
                        selected = state.selectedTab == MechanicDashboardTab.MY_JOBS,
                        onClick = { model.selectTab(MechanicDashboardTab.MY_JOBS) },
                        text = { Text("My Jobs") },
                    )
                }

                when {
                    state.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    state.error != null -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(state.error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = model::refresh) { Text("Retry") }
                        }
                    }
                    state.selectedTab == MechanicDashboardTab.CLIENT_JOBS -> {
                        ClientJobsTab(
                            assignments = state.assignments,
                            completingId = state.completingId,
                            onComplete = model::completeJob,
                            onViewVehicle = { assignment ->
                                navigator.push(MechanicVehicleViewScreen(assignment.vehicleId, assignment.id))
                            },
                        )
                    }
                    state.selectedTab == MechanicDashboardTab.MY_JOBS -> {
                        MyJobsTab(
                            jobs = state.myJobs,
                            onJobClick = { job -> navigator.push(MechanicJobDetailScreen(job.id)) },
                            onCreateJob = { navigator.push(CreateMechanicJobScreen()) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientJobsTab(
    assignments: List<MechanicAssignment>,
    completingId: String?,
    onComplete: (String) -> Unit,
    onViewVehicle: (MechanicAssignment) -> Unit,
) {
    if (assignments.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No active client jobs right now.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(48.dp),
            )
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(assignments, key = { it.id }) { assignment ->
                AssignmentCard(
                    assignment = assignment,
                    isCompleting = completingId == assignment.id,
                    onComplete = { onComplete(assignment.id) },
                    onViewVehicle = { onViewVehicle(assignment) },
                )
            }
        }
    }
}

@Composable
private fun MyJobsTab(
    jobs: List<MechanicJob>,
    onJobClick: (MechanicJob) -> Unit,
    onCreateJob: () -> Unit,
) {
    if (jobs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(48.dp)) {
                Text(
                    text = "No jobs yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Tap + to create a job for any client, even if they're not in the app yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(onClick = onCreateJob) {
                    Text("Create a Job")
                }
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(jobs, key = { it.id }) { job ->
                MyJobCard(job = job, onClick = { onJobClick(job) })
            }
        }
    }
}

@Composable
private fun MyJobCard(job: MechanicJob, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = job.clientName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (job.status == "open") "Open" else "Completed",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (job.status == "open") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${job.vehicleYear} ${job.vehicleMake} ${job.vehicleModel}",
                style = MaterialTheme.typography.bodyMedium,
            )
            job.description?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Created ${job.createdAt.take(10)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
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
