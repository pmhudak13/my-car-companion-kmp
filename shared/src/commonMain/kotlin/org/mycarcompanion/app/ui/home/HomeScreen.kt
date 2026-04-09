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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
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
import org.mycarcompanion.app.data.models.AuthResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.mycarcompanion.app.data.models.AuthState
import org.mycarcompanion.app.data.models.Vehicle
import org.mycarcompanion.app.ui.admin.AdminScreen
import org.mycarcompanion.app.ui.auth.LoginScreen
import org.mycarcompanion.app.ui.mechanics.MechanicDashboardScreen
import org.mycarcompanion.app.ui.mechanics.MechanicDirectoryScreen
import org.mycarcompanion.app.ui.mechanics.MechanicSetupScreen
import org.mycarcompanion.app.ui.mileage.MileageTrackerScreen
import org.mycarcompanion.app.ui.vehicles.AddVehicleScreen
import org.mycarcompanion.app.ui.vehicles.VehicleCard
import org.mycarcompanion.app.ui.vehicles.VehicleDetailScreen

class HomeScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: HomeScreenModel = koinScreenModel()
        val authState by model.authState.collectAsState()
        val vehicleState by model.vehicleState.collectAsState()
        val linkState by model.linkState.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(authState) {
            when (val s = authState) {
                is AuthState.Unauthenticated -> navigator.replaceAll(LoginScreen())
                is AuthState.Authenticated -> {
                    // Admins stay on HomeScreen and can switch views manually
                    if (s.user.isMechanic && !s.user.isAdmin) {
                        navigator.replace(MechanicDashboardScreen())
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { navigator.push(AddVehicleScreen()) },
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
            }
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
                    Column {
                        Text(
                            text = "My Car Companion",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = user.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = model::signOut,
                    ) {
                        Text("Sign Out", color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { navigator.push(MechanicDirectoryScreen()) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Text("Find Mechanic", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick = { navigator.push(MileageTrackerScreen()) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Text("Mileage Tracker", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (!user.hasGoogleLinked) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = model::linkGoogleAccount,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Link Google Account", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (user.isAdmin) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { navigator.push(AdminScreen()) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ),
                        ) {
                            Text("Admin Panel", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = { navigator.push(MechanicDashboardScreen()) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        ) {
                            Text("Mechanic View", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Pending mechanic: signed up as mechanic but not yet approved
                if (!user.isMechanic && user.intendedRole == "mechanic") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { navigator.push(MechanicSetupScreen()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Complete Mechanic Profile Setup", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "My Vehicles",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(12.dp))

                when {
                    vehicleState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    vehicleState.error != null -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = vehicleState.error ?: "Unknown error",
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = model::loadVehicles) {
                                Text("Retry")
                            }
                        }
                    }
                    vehicleState.vehicles.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "No vehicles yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap + to add your first vehicle",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 80.dp),
                        ) {
                            items(vehicleState.vehicles, key = { it.id }) { vehicle ->
                                VehicleCard(
                                    vehicle = vehicle,
                                    onClick = { navigator.push(VehicleDetailScreen(vehicle.id)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
