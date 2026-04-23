package org.mycarcompanion.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.mycarcompanion.app.ui.auth.LoginScreen
import org.mycarcompanion.app.ui.help.HelpScreen
import org.mycarcompanion.app.ui.subscription.SubscribeScreen
import org.mycarcompanion.app.ui.notifications.NotificationsScreen
import org.mycarcompanion.app.ui.profile.ProfileScreen
import org.mycarcompanion.app.ui.reminders.RemindersListScreen
import org.mycarcompanion.app.ui.transfer.ReceiveTransferScreen

class SettingsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val uriHandler = LocalUriHandler.current
        val model: SettingsScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        var showSignOutConfirm by remember { mutableStateOf(false) }
        var showDeleteConfirm by remember { mutableStateOf(false) }
        val snackbarState = remember { SnackbarHostState() }

        LaunchedEffect(state.signedOut) {
            if (state.signedOut) navigator.replaceAll(LoginScreen())
        }

        LaunchedEffect(state.deleteError) {
            val err = state.deleteError ?: return@LaunchedEffect
            snackbarState.showSnackbar(err)
            model.clearDeleteError()
        }

        if (showSignOutConfirm) {
            AlertDialog(
                onDismissRequest = { showSignOutConfirm = false },
                title = { Text("Sign Out") },
                text = { Text("Are you sure you want to sign out?") },
                confirmButton = {
                    TextButton(onClick = {
                        showSignOutConfirm = false
                        model.signOut()
                    }) { Text("Sign Out") }
                },
                dismissButton = {
                    TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel") }
                },
            )
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete Account") },
                text = {
                    Text(
                        "This will permanently delete your account and all associated data. " +
                        "This action cannot be undone."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            model.deleteAccount()
                        },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Delete Account") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                },
            )
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarState) },
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { paddingValues ->
            if (state.signingOut || state.deletingAccount) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    SectionHeader("Account")
                    SettingsRow(
                        icon = Icons.Default.AccountCircle,
                        label = "Profile",
                        onClick = { navigator.push(ProfileScreen()) },
                    )
                    SettingsRow(
                        icon = Icons.Default.Star,
                        label = if (state.isPremium) "Manage Subscription" else "Upgrade to Premium",
                        onClick = { navigator.push(SubscribeScreen()) },
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    SettingsRow(
                        icon = Icons.Default.Notifications,
                        label = "Reminders",
                        onClick = { navigator.push(RemindersListScreen()) },
                    )
                    SettingsRow(
                        icon = Icons.Default.NotificationsNone,
                        label = "Notifications",
                        onClick = { navigator.push(NotificationsScreen()) },
                    )
                    SettingsRow(
                        icon = Icons.Default.SwapHoriz,
                        label = "Receive Vehicle Transfer",
                        onClick = { navigator.push(ReceiveTransferScreen()) },
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader("Legal")
                    SettingsRow(
                        icon = Icons.Default.Email,
                        label = "Privacy Policy",
                        onClick = { uriHandler.openUri("https://mycarcompanion.app/privacy") },
                    )
                    SettingsRow(
                        icon = Icons.Default.Email,
                        label = "Terms of Service",
                        onClick = { uriHandler.openUri("https://mycarcompanion.app/terms") },
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader("Support")
                    SettingsRow(
                        icon = Icons.Default.HelpOutline,
                        label = "Help & FAQ",
                        onClick = { navigator.push(HelpScreen()) },
                    )
                    SettingsRow(
                        icon = Icons.Default.Phone,
                        label = "Text Us About an Issue",
                        onClick = {
                            uriHandler.openUri("sms:+15622658148?body=[MyCarCompanion] Issue: ")
                        },
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader("Account Actions")
                    SettingsRow(
                        icon = Icons.Default.ExitToApp,
                        label = "Sign Out",
                        onClick = { showSignOutConfirm = true },
                        tint = MaterialTheme.colorScheme.error,
                    )
                    SettingsRow(
                        icon = Icons.Default.Delete,
                        label = "Delete Account",
                        onClick = { showDeleteConfirm = true },
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (tint != androidx.compose.ui.graphics.Color.Unspecified) tint else MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = tint.takeIf { it != androidx.compose.ui.graphics.Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
