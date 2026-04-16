package org.mycarcompanion.app.ui.invoices

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.MechanicInvoice
import org.mycarcompanion.app.data.models.MechanicInvoiceItem
import org.mycarcompanion.app.data.repository.MechanicInvoiceRepository

// ─── Screen Model ──────────────────────────────────────────────────────────────

data class ClientInvoiceState(
    val invoices: List<MechanicInvoice> = emptyList(),
    val selectedInvoice: MechanicInvoice? = null,
    val selectedItems: List<MechanicInvoiceItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class ClientInvoiceScreenModel(
    private val invoiceRepository: MechanicInvoiceRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(ClientInvoiceState())
    val state: StateFlow<ClientInvoiceState> = _state.asStateFlow()

    fun load() {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            invoiceRepository.getMyClientInvoices()
                .onSuccess { invoices ->
                    _state.value = _state.value.copy(
                        invoices = invoices,
                        isLoading = false,
                        selectedInvoice = invoices.firstOrNull(),
                    )
                    invoices.firstOrNull()?.let { loadItems(it.id) }
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    fun selectInvoice(invoice: MechanicInvoice) {
        _state.value = _state.value.copy(selectedInvoice = invoice, selectedItems = emptyList())
        loadItems(invoice.id)
    }

    private fun loadItems(invoiceId: String) {
        screenModelScope.launch {
            invoiceRepository.getItemsForInvoice(invoiceId)
                .onSuccess { items ->
                    _state.value = _state.value.copy(selectedItems = items)
                }
        }
    }
}

// ─── Screen ────────────────────────────────────────────────────────────────────

class ClientInvoiceScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: ClientInvoiceScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) { model.load() }
        LaunchedEffect(state.error) {
            state.error?.let { snackbarHostState.showSnackbar(it) }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("My Invoices") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { paddingValues ->
            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.invoices.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No invoices yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Invoice list if more than one
                    if (state.invoices.size > 1) {
                        item {
                            Text("All Invoices", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        items(state.invoices, key = { it.id }) { inv ->
                            ClientInvoiceSummaryCard(
                                invoice = inv,
                                isSelected = state.selectedInvoice?.id == inv.id,
                                onClick = { model.selectInvoice(inv) },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(4.dp)) }
                    }

                    // Detail view
                    state.selectedInvoice?.let { invoice ->
                        item {
                            ClientInvoiceDetailCard(
                                invoice = invoice,
                                items = state.selectedItems,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientInvoiceSummaryCard(
    invoice: MechanicInvoice,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(invoice.invoiceNumber, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(invoice.createdAt.take(10), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$${formatClientAmount(invoice.total)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ClientStatusLabel(invoice.status)
            }
        }
    }
}

@Composable
private fun ClientInvoiceDetailCard(
    invoice: MechanicInvoice,
    items: List<MechanicInvoiceItem>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(invoice.invoiceNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ClientStatusLabel(invoice.status)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("Issued ${invoice.createdAt.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            invoice.dueDate?.let {
                Text("Due $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            invoice.paidAt?.let {
                Text("Paid ${it.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            if (items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${item.description} × ${formatClientQty(item.quantity)}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text("$${formatClientAmount(item.total)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Subtotal", style = MaterialTheme.typography.bodySmall)
                Text("$${formatClientAmount(invoice.subtotal)}", style = MaterialTheme.typography.bodySmall)
            }
            if (invoice.taxAmount > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tax (${(invoice.taxRate * 100).toInt()}%)", style = MaterialTheme.typography.bodySmall)
                    Text("$${formatClientAmount(invoice.taxAmount)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("$${formatClientAmount(invoice.total)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }

            invoice.notes?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Stripe payment link
            invoice.stripePaymentLinkUrl?.let { url ->
                if (invoice.status == "sent") {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { /* TODO: open URL in browser via platform expect */ },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Pay Now")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Secure payment via Stripe",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClientStatusLabel(status: String) {
    val (label, color) = when (status) {
        "draft" -> "Draft" to MaterialTheme.colorScheme.outline
        "sent" -> "Payment Due" to MaterialTheme.colorScheme.error
        "paid" -> "Paid" to MaterialTheme.colorScheme.primary
        "cancelled" -> "Cancelled" to MaterialTheme.colorScheme.outline
        else -> status to MaterialTheme.colorScheme.outline
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
}

private fun formatClientAmount(value: Double): String {
    val intPart = value.toLong()
    val fracPart = ((value - intPart) * 100 + 0.5).toLong()
    return "$intPart.${fracPart.toString().padStart(2, '0')}"
}

private fun formatClientQty(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
