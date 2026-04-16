package org.mycarcompanion.app.ui.subscription

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.mycarcompanion.app.data.repository.SubscriptionRepository

class SubscribeScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: SubscribeScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val uriHandler = LocalUriHandler.current
        val snackbarState = remember { SnackbarHostState() }

        // When a checkout URL arrives, open it in the browser
        LaunchedEffect(state.checkoutUrl) {
            val url = state.checkoutUrl ?: return@LaunchedEffect
            uriHandler.openUri(url)
            model.clearCheckoutUrl()
        }

        // Show errors as snackbar
        LaunchedEffect(state.error) {
            val err = state.error ?: return@LaunchedEffect
            snackbarState.showSnackbar(err)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Go Premium") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarState) },
        ) { paddingValues ->
            if (state.loading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Upgrade Your Experience",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Get full access to all features with a Premium subscription.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    PlanSection(
                        title = "Premium",
                        subtitle = "For car owners",
                        features = listOf(
                            "Unlimited vehicles",
                            "Full maintenance history",
                            "Mileage tracking & reports",
                            "Message mechanics directly",
                            "Priority support",
                        ),
                        monthlyPriceId = SubscriptionRepository.PRICE_PREMIUM_MONTHLY,
                        yearlyPriceId = SubscriptionRepository.PRICE_PREMIUM_YEARLY,
                        monthlyLabel = "$4.99 / month",
                        yearlyLabel = "$49.99 / year  (save 17%)",
                        onCheckout = { model.startCheckout(it) },
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    PlanSection(
                        title = "Mechanic Pro",
                        subtitle = "For mechanics & shops",
                        features = listOf(
                            "Everything in Premium",
                            "Mechanic profile & directory listing",
                            "Manage customer jobs",
                            "Customer messaging inbox",
                            "Pro badge on your profile",
                        ),
                        monthlyPriceId = SubscriptionRepository.PRICE_MECHANIC_MONTHLY,
                        yearlyPriceId = SubscriptionRepository.PRICE_MECHANIC_YEARLY,
                        monthlyLabel = "$14.99 / month",
                        yearlyLabel = "$149.99 / year  (save 17%)",
                        onCheckout = { model.startCheckout(it) },
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "Cancel anytime. Billed securely via Stripe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun PlanSection(
    title: String,
    subtitle: String,
    features: List<String>,
    monthlyPriceId: String,
    yearlyPriceId: String,
    monthlyLabel: String,
    yearlyLabel: String,
    onCheckout: (String) -> Unit,
) {
    var selectedYearly by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            features.forEach { feature ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 3.dp),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                    Text(feature, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Billing toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BillingToggleButton(
                    label = "Monthly",
                    sublabel = monthlyLabel,
                    selected = !selectedYearly,
                    onClick = { selectedYearly = false },
                    modifier = Modifier.weight(1f),
                )
                BillingToggleButton(
                    label = "Yearly",
                    sublabel = yearlyLabel,
                    selected = selectedYearly,
                    onClick = { selectedYearly = true },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    onCheckout(if (selectedYearly) yearlyPriceId else monthlyPriceId)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Subscribe — ${if (selectedYearly) yearlyLabel else monthlyLabel}")
            }
        }
    }
}

@Composable
private fun BillingToggleButton(
    label: String,
    sublabel: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(sublabel, style = MaterialTheme.typography.labelSmall)
            }
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(sublabel, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
