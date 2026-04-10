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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.mycarcompanion.app.ui.auth.LoginScreen
import org.mycarcompanion.app.ui.help.HelpScreen
import org.mycarcompanion.app.ui.messaging.MessagesListScreen
import org.mycarcompanion.app.ui.notifications.NotificationsScreen
import org.mycarcompanion.app.ui.profile.ProfileScreen
import org.mycarcompanion.app.ui.reminders.RemindersListScreen

class SettingsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: SettingsScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        var showSignOutConfirm by remember { mutableStateOf(false) }

        LaunchedEffect(state.signedOut) {
            if (state.signedOut) navigator.replaceAll(LoginScreen())
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

        Scaffold(
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
            if (state.signingOut) {
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
                        icon = Icons.Default.Notifications,
                        label = "Reminders",
                        onClick = { navigator.push(RemindersListScreen()) },
                    )
                    SettingsRow(
                        icon = Icons.Default.Email,
                        label = "Messages",
                        onClick = { navigator.push(MessagesListScreen()) },
                    )
                    SettingsRow(
                        icon = Icons.Default.NotificationsNone,
                        label = "Notifications",
                        onClick = { navigator.push(NotificationsScreen()) },
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader("Support")
                    SettingsRow(
                        icon = Icons.Default.HelpOutline,
                        label = "Help & FAQ",
                        onClick = { navigator.push(HelpScreen()) },
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
