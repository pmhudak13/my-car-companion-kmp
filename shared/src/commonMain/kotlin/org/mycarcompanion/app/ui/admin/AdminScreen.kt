package org.mycarcompanion.app.ui.admin

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import org.mycarcompanion.app.data.models.AdminUserEntry

class AdminScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: AdminScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        var giftDialogUser by remember { mutableStateOf<AdminUserEntry?>(null) }
        var giftReason by remember { mutableStateOf("") }

        Scaffold { paddingValues ->
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Admin Panel",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                TextButton(onClick = { navigator.pop() }) { Text("Back") }
                            }
                        }

                        state.error?.let { error ->
                            item {
                                Text(
                                    error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        state.successMessage?.let { msg ->
                            item {
                                Text(
                                    msg,
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        item {
                            Text(
                                "${state.users.size} users",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        items(state.users, key = { it.userId }) { user ->
                            UserCard(
                                user = user,
                                isActioning = state.actionUserId == user.userId,
                                onGiftPremium = { giftDialogUser = user },
                                onRevokePremium = { model.revokePremium(user.userId) },
                            )
                        }
                    }
                }
            }
        }

        // Gift premium dialog
        giftDialogUser?.let { user ->
            AlertDialog(
                onDismissRequest = {
                    giftDialogUser = null
                    giftReason = ""
                },
                title = { Text("Gift Premium") },
                text = {
                    Column {
                        Text("Gift premium access to ${user.email}?")
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = giftReason,
                            onValueChange = { giftReason = it },
                            label = { Text("Reason (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            model.giftPremium(user.userId, giftReason.ifBlank { null })
                            giftDialogUser = null
                            giftReason = ""
                        },
                    ) {
                        Text("Gift Premium")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            giftDialogUser = null
                            giftReason = ""
                        },
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun UserCard(
    user: AdminUserEntry,
    isActioning: Boolean,
    onGiftPremium: () -> Unit,
    onRevokePremium: () -> Unit,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoleBadge(user.role)
                        if (user.isPremium) {
                            PremiumBadge()
                        }
                    }
                }
                if (isActioning) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else if (user.isPremium) {
                    OutlinedButton(
                        onClick = onRevokePremium,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Revoke", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Button(onClick = onGiftPremium) {
                        Text("Gift Premium", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleBadge(role: String) {
    val color = when (role) {
        "admin" -> MaterialTheme.colorScheme.error
        "mechanic" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    Text(
        text = role.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun PremiumBadge() {
    Text(
        text = "PREMIUM",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}
