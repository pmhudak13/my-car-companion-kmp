package org.mycarcompanion.app.ui.admin

import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.mycarcompanion.app.data.models.AdminUserEntry
import org.mycarcompanion.app.data.models.MechanicProfile
import org.mycarcompanion.app.data.models.shopTypeLabels

class AdminScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: AdminScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        var giftDialogUser by remember { mutableStateOf<AdminUserEntry?>(null) }
        var giftReason by remember { mutableStateOf("") }
        var revokePremiumUser by remember { mutableStateOf<AdminUserEntry?>(null) }
        var revokeMechanicUser by remember { mutableStateOf<AdminUserEntry?>(null) }
        var revokeMechanicProfile by remember { mutableStateOf<MechanicProfile?>(null) }
        var selectedTab by remember { mutableStateOf(0) }

        LaunchedEffect(state.successMessage) {
            if (state.successMessage != null) {
                delay(3000)
                model.clearMessage()
            }
        }

        Scaffold { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Users") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Mechanics") },
                    )
                }

                when (selectedTab) {
                    0 -> {
                        // Existing users tab content
                        when {
                            state.isLoading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
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
                                            onRevokePremium = { revokePremiumUser = user },
                                            onConvertToMechanic = { model.convertToMechanic(user.userId) },
                                            onRevokeMechanic = { revokeMechanicUser = user },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Mechanics tab content
                        when {
                            state.mechanicsLoading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    state.mechanicsError?.let { error ->
                                        item {
                                            Text(
                                                error,
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                    }

                                    item {
                                        Text(
                                            "${state.mechanics.size} mechanics",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    items(state.mechanics, key = { it.userId }) { mechanic ->
                                        MechanicAdminCard(
                                            mechanic = mechanic,
                                            processingId = state.processingMechanicId,
                                            onApprove = { model.approveMechanic(mechanic.userId) },
                                            onReject = { model.rejectMechanic(mechanic.userId) },
                                            onRevoke = { revokeMechanicProfile = mechanic },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Revoke premium confirmation
        revokePremiumUser?.let { user ->
            AlertDialog(
                onDismissRequest = { revokePremiumUser = null },
                title = { Text("Revoke Premium") },
                text = { Text("Remove premium access for ${user.email}?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            model.revokePremium(user.userId)
                            revokePremiumUser = null
                        },
                    ) { Text("Revoke", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { revokePremiumUser = null }) { Text("Cancel") }
                },
            )
        }

        // Revoke mechanic role confirmation (Users tab)
        revokeMechanicUser?.let { user ->
            AlertDialog(
                onDismissRequest = { revokeMechanicUser = null },
                title = { Text("Revoke Mechanic Role") },
                text = { Text("Remove mechanic role from ${user.email}? They will lose access to the mechanic dashboard.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            model.revokeMechanicRole(user.userId)
                            revokeMechanicUser = null
                        },
                    ) { Text("Revoke", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { revokeMechanicUser = null }) { Text("Cancel") }
                },
            )
        }

        // Revoke verified mechanic confirmation (Mechanics tab)
        revokeMechanicProfile?.let { mechanic ->
            AlertDialog(
                onDismissRequest = { revokeMechanicProfile = null },
                title = { Text("Revoke Mechanic") },
                text = { Text("Revoke verification for ${mechanic.shopName ?: "this mechanic"}? They will be removed from the directory.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            model.revokeMechanicRole(mechanic.userId)
                            revokeMechanicProfile = null
                        },
                    ) { Text("Revoke", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { revokeMechanicProfile = null }) { Text("Cancel") }
                },
            )
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
    onConvertToMechanic: () -> Unit,
    onRevokeMechanic: () -> Unit,
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
            if (!isActioning && user.role == "owner") {
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onConvertToMechanic,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Make Mechanic", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (!isActioning && user.role == "mechanic") {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRevokeMechanic,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Revoke Mechanic Role", style = MaterialTheme.typography.labelSmall)
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

@Composable
private fun MechanicAdminCard(
    mechanic: MechanicProfile,
    processingId: String?,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onRevoke: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Shop name + verification badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = mechanic.shopName ?: "Unnamed",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                VerificationBadge(mechanic.verificationStatus)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Shop type + location
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = shopTypeLabels[mechanic.shopType] ?: mechanic.shopType,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val location = listOfNotNull(mechanic.city, mechanic.state).joinToString(", ")
                if (location.isNotEmpty()) {
                    Text(
                        text = location,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Experience + rate
            val hasExtra = mechanic.yearsExperience != null || mechanic.hourlyRate != null
            if (hasExtra) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    mechanic.yearsExperience?.let {
                        Text(
                            text = "${it}yr exp",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    mechanic.hourlyRate?.let {
                        Text(
                            text = "$$it/hr",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action area based on verification status
            when (mechanic.verificationStatus) {
                "pending" -> {
                    if (processingId == mechanic.userId) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = onApprove) {
                                Text("Approve")
                            }
                            OutlinedButton(onClick = onReject) {
                                Text("Reject")
                            }
                        }
                    }
                }
                "verified" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "✓ Verified",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (processingId == mechanic.userId) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            OutlinedButton(
                                onClick = onRevoke,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Text("Revoke", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                "rejected" -> {
                    Text(
                        text = "✗ Rejected",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun VerificationBadge(status: String) {
    val (text, color, borderColor) = when (status) {
        "verified" -> Triple(
            "VERIFIED",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary,
        )
        "rejected" -> Triple(
            "REJECTED",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error,
        )
        else -> Triple(
            "PENDING",
            MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.outline,
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
