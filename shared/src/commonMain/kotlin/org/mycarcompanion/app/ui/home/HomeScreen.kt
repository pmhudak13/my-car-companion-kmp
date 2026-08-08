package org.mycarcompanion.app.ui.home

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.mycarcompanion.app.data.models.AuthResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.mycarcompanion.app.data.models.AuthState
import org.mycarcompanion.app.data.models.Reminder
import org.mycarcompanion.app.data.models.Vehicle
import org.mycarcompanion.app.data.models.reminderTypeLabels
import org.mycarcompanion.app.ui.admin.AdminScreen
import org.mycarcompanion.app.ui.auth.LoginScreen
import org.mycarcompanion.app.ui.messaging.MessagesListScreen
import org.mycarcompanion.app.ui.settings.SettingsScreen
import org.mycarcompanion.app.ui.mechanics.MechanicDashboardScreen
import org.mycarcompanion.app.ui.mechanics.JobBoardScreen
import org.mycarcompanion.app.ui.mechanics.MechanicDirectoryScreen
import org.mycarcompanion.app.ui.mechanics.MechanicSetupScreen
import org.mycarcompanion.app.ui.mileage.MileageTrackerScreen
import org.mycarcompanion.app.ui.vehicles.AddVehicleScreen
import org.mycarcompanion.app.ui.vehicles.VehicleCard
import org.mycarcompanion.app.ui.vehicles.VehicleDetailScreen
import org.mycarcompanion.app.platform.scaffoldContentWindowInsets
import org.mycarcompanion.app.platform.topBarWindowInsets

@OptIn(ExperimentalMaterial3Api::class)
class HomeScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: HomeScreenModel = koinScreenModel()
        val authState by model.authState.collectAsState()
        val vehicleState by model.vehicleState.collectAsState()
        val linkState by model.linkState.collectAsState()
        val unreadCount by model.unreadCount.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        // Voyager only composes the top screen, so this re-runs whenever the user
        // navigates back here — picking up vehicles added on child screens.
        LaunchedEffect(Unit) { model.refresh() }

        LaunchedEffect(authState) {
            when (val s = authState) {
                is AuthState.Unauthenticated -> navigator.replaceAll(LoginScreen())
                is AuthState.Authenticated -> {
                    if (s.user.isMechanic && !s.user.isAdmin) {
                        // Approved mechanic → mechanic dashboard
                        navigator.replace(MechanicDashboardScreen())
                    } else if (!s.user.isMechanic && !s.user.isAdmin && s.user.intendedRole == "mechanic") {
                        // New or pending mechanic → setup/pending screen
                        navigator.replace(MechanicSetupScreen())
                    }
                }
                else -> Unit
            }
        }

        LaunchedEffect(linkState) {
            when (val state = linkState) {
                is AuthResult.Success -> {
                    snackbarHostState.showSnackbar("Google account linked successfully")
                    model.clearLinkState()
                }
                is AuthResult.Error -> {
                    snackbarHostState.showSnackbar(state.message)
                    model.clearLinkState()
                }
                null -> Unit
            }
        }

        val user = (authState as? AuthState.Authenticated)?.user ?: return

        Scaffold(
            contentWindowInsets = scaffoldContentWindowInsets(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "My Car Companion",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = user.email,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { navigator.push(SettingsScreen()) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    windowInsets = topBarWindowInsets(),
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { navigator.push(AddVehicleScreen()) },
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Vehicle")
                }
            }
        ) { paddingValues ->
            PullToRefreshBox(
                isRefreshing = vehicleState.isRefreshing,
                onRefresh = { model.refresh(fromPull = true) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            QuickActionCard(
                                icon = Icons.Default.Build,
                                label = "Find Mechanic",
                                onClick = { navigator.push(MechanicDirectoryScreen()) },
                                modifier = Modifier.weight(1f),
                            )
                            QuickActionCard(
                                icon = Icons.Default.Speed,
                                label = "Mileage",
                                onClick = { navigator.push(MileageTrackerScreen()) },
                                modifier = Modifier.weight(1f),
                            )
                            QuickActionCard(
                                icon = Icons.Default.Email,
                                label = "Messages",
                                onClick = { navigator.push(MessagesListScreen()) },
                                modifier = Modifier.weight(1f),
                                badgeCount = unreadCount,
                            )
                            QuickActionCard(
                                icon = Icons.Default.Campaign,
                                label = "Post Job",
                                onClick = { navigator.push(JobBoardScreen()) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    if (vehicleState.upcomingReminders.isNotEmpty()) {
                        item {
                            Text(
                                text = "Upcoming",
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        items(vehicleState.upcomingReminders, key = { it.id }) { reminder ->
                            val vehicle = vehicleState.vehicles.find { it.id == reminder.vehicleId }
                            UpcomingReminderCard(
                                reminder = reminder,
                                vehicleName = vehicle?.let { "${it.year} ${it.make} ${it.model}" } ?: "",
                                onClick = { navigator.push(VehicleDetailScreen(reminder.vehicleId)) },
                            )
                        }
                    }

                    if (!user.hasGoogleLinked) {
                        item {
                            OutlinedButton(
                                onClick = model::linkGoogleAccount,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Link Google Account")
                            }
                        }
                    }

                    if (user.isAdmin) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = { navigator.push(AdminScreen()) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    ),
                                ) {
                                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Admin Panel")
                                }
                                Button(
                                    onClick = { navigator.push(MechanicDashboardScreen()) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                                ) {
                                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Mechanic View")
                                }
                            }
                        }
                    }

                    if (!user.isMechanic && user.intendedRole == "mechanic") {
                        item {
                            OutlinedButton(
                                onClick = { navigator.push(MechanicSetupScreen()) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Complete Mechanic Profile Setup")
                            }
                        }
                    }

                    item {
                        Text(
                            text = "My Vehicles",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }

                    when {
                        vehicleState.isLoading -> {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                        vehicleState.error != null -> {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = vehicleState.error ?: "Unknown error",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(onClick = model::refresh) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                        vehicleState.vehicles.isEmpty() -> {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        Icons.Default.DirectionsCar,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.outline,
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No vehicles yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap + to add your first vehicle",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                        else -> {
                            items(vehicleState.vehicles, key = { it.id }) { vehicle ->
                                VehicleCard(
                                    vehicle = vehicle,
                                    onClick = { navigator.push(VehicleDetailScreen(vehicle.id)) },
                                )
                            }
                            item { Spacer(modifier = Modifier.height(68.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingReminderCard(
    reminder: Reminder,
    vehicleName: String,
    onClick: () -> Unit,
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()).toString() }
    val overdue = reminder.nextDueDate != null && reminder.nextDueDate < today
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (overdue) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.customName
                        ?: reminderTypeLabels[reminder.type]
                        ?: reminder.type,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (vehicleName.isNotBlank()) {
                    Text(
                        text = vehicleName,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val dueText = when {
                    overdue -> "Overdue — was due ${reminder.nextDueDate}"
                    reminder.nextDueDate != null -> "Due ${reminder.nextDueDate}"
                    reminder.nextDueMileage != null -> "Due at ${reminder.nextDueMileage} mi"
                    else -> ""
                }
                if (dueText.isNotEmpty()) {
                    Text(
                        text = dueText,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BadgedBox(
                badge = {
                    if (badgeCount > 0) {
                        Badge { Text(badgeCount.toString()) }
                    }
                },
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
