package org.mycarcompanion.app.ui.mechanics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.mycarcompanion.app.data.models.InvoiceForm
import org.mycarcompanion.app.data.models.InvoiceLineItemForm
import org.mycarcompanion.app.data.models.MechanicInvoice
import org.mycarcompanion.app.data.models.MechanicInvoiceItem
import org.mycarcompanion.app.platform.CommonParcelable

data class MechanicInvoiceScreen(val jobId: String) : Screen, CommonParcelable {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: MechanicInvoiceScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        LaunchedEffect(jobId) { model.load(jobId) }
        LaunchedEffect(state.successMessage) {
            state.successMessage?.let {
                snackbarHostState.showSnackbar(it)
                model.clearMessages()
            }
        }
        LaunchedEffect(state.error) {
            state.error?.let {
                snackbarHostState.showSnackbar(it)
                model.clearMessages()
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Invoices") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        TextButton(onClick = model::showCreateForm) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New")
                        }
                    },
                )
            },
        ) { paddingValues ->
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.existingInvoices.isEmpty()) {
                        item {
                            Text(
                                "No invoices yet. Tap + New to create one.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        // Invoice list for selection if multiple
                        if (state.existingInvoices.size > 1) {
                            item {
                                Text(
                                    "All Invoices",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            items(state.existingInvoices, key = { it.id }) { inv ->
                                InvoiceSummaryCard(
                                    invoice = inv,
                                    isSelected = state.invoice?.id == inv.id,
                                    onClick = { model.selectInvoice(inv) },
                                )
                            }
                        }

                        // Detail for selected invoice
                        state.invoice?.let { invoice ->
                            item {
                                InvoiceDetailCard(
                                    invoice = invoice,
                                    items = state.items,
                                    isSending = state.isSending,
                                    isCreatingPaymentLink = state.isCreatingPaymentLink,
                                    onSend = model::sendInvoice,
                                    onMarkPaid = model::markPaid,
                                    onCreateStripeLink = model::createStripePaymentLink,
                                )
                            }
                        }
                    }
                }
            }

            // Create invoice bottom sheet
            if (state.showCreateForm) {
                ModalBottomSheet(
                    onDismissRequest = model::hideCreateForm,
                    sheetState = bottomSheetState,
                ) {
                    CreateInvoiceSheet(
                        form = state.form,
                        isSaving = state.isSaving,
                        error = state.error,
                        onFormUpdate = model::updateForm,
                        onAddItem = model::addLineItem,
                        onRemoveItem = model::removeLineItem,
                        onUpdateItem = model::updateLineItem,
                        onSave = { model.createInvoice(jobId) },
                        onCancel = model::hideCreateForm,
                    )
                }
            }
        }
    }
}

@Composable
private fun InvoiceSummaryCard(
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
                Text("$${formatInvoiceAmount(invoice.total)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                InvoiceStatusChip(invoice.status)
            }
        }
    }
}

@Composable
private fun InvoiceDetailCard(
    invoice: MechanicInvoice,
    items: List<MechanicInvoiceItem>,
    isSending: Boolean,
    isCreatingPaymentLink: Boolean,
    onSend: () -> Unit,
    onMarkPaid: () -> Unit,
    onCreateStripeLink: () -> Unit,
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
                InvoiceStatusChip(invoice.status)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Created ${invoice.createdAt.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            invoice.dueDate?.let {
                Text("Due $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            invoice.paidAt?.let {
                Text("Paid ${it.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Line items
            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${item.description} × ${formatQty(item.quantity)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "$${formatInvoiceAmount(item.total)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Totals
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Subtotal", style = MaterialTheme.typography.bodySmall)
                Text("$${formatInvoiceAmount(invoice.subtotal)}", style = MaterialTheme.typography.bodySmall)
            }
            if (invoice.taxAmount > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tax (${(invoice.taxRate * 100).toInt()}%)", style = MaterialTheme.typography.bodySmall)
                    Text("$${formatInvoiceAmount(invoice.taxAmount)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("$${formatInvoiceAmount(invoice.total)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }

            invoice.notes?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Stripe payment link
            invoice.stripePaymentLinkUrl?.let { url ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Payment link: $url",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Action buttons
            if (invoice.status != "cancelled" && invoice.status != "paid") {
                Spacer(modifier = Modifier.height(12.dp))
                when (invoice.status) {
                    "draft" -> {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.height(36.dp))
                        } else {
                            Button(onClick = onSend, modifier = Modifier.fillMaxWidth()) {
                                Text("Send Invoice to Client")
                            }
                        }
                    }
                    "sent" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Basic tier: manual mark paid
                            OutlinedButton(onClick = onMarkPaid, modifier = Modifier.fillMaxWidth()) {
                                Text("Mark as Paid")
                            }
                            // Stripe tier: payment link
                            if (invoice.stripePaymentLinkUrl == null) {
                                if (isCreatingPaymentLink) {
                                    CircularProgressIndicator(modifier = Modifier.height(36.dp))
                                } else {
                                    FilledTonalButton(onClick = onCreateStripeLink, modifier = Modifier.fillMaxWidth()) {
                                        Text("Create Stripe Payment Link")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceStatusChip(status: String) {
    val (label, color) = when (status) {
        "draft" -> "Draft" to MaterialTheme.colorScheme.outline
        "sent" -> "Sent" to MaterialTheme.colorScheme.primary
        "paid" -> "Paid" to MaterialTheme.colorScheme.tertiary
        "cancelled" -> "Cancelled" to MaterialTheme.colorScheme.error
        else -> status to MaterialTheme.colorScheme.outline
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CreateInvoiceSheet(
    form: InvoiceForm,
    isSaving: Boolean,
    error: String?,
    onFormUpdate: (InvoiceForm) -> Unit,
    onAddItem: () -> Unit,
    onRemoveItem: (String) -> Unit,
    onUpdateItem: (InvoiceLineItemForm) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("New Invoice", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text("Line Items *", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        form.items.forEachIndexed { index, item ->
            LineItemRow(
                item = item,
                showDelete = form.items.size > 1,
                onUpdate = onUpdateItem,
                onDelete = { onRemoveItem(item.id) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        TextButton(onClick = onAddItem) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Line Item")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = form.taxRate,
            onValueChange = { onFormUpdate(form.copy(taxRate = it)) },
            label = { Text("Tax Rate (%)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = form.dueDate,
            onValueChange = { onFormUpdate(form.copy(dueDate = it)) },
            label = { Text("Due Date (YYYY-MM-DD)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = form.notes,
            onValueChange = { onFormUpdate(form.copy(notes = it)) },
            label = { Text("Notes") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSave,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Create Invoice")
            }
        }
    }
}

@Composable
private fun LineItemRow(
    item: InvoiceLineItemForm,
    showDelete: Boolean,
    onUpdate: (InvoiceLineItemForm) -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = item.description,
                    onValueChange = { onUpdate(item.copy(description = it)) },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (showDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = item.quantity,
                    onValueChange = { onUpdate(item.copy(quantity = it)) },
                    label = { Text("Qty") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = item.unitPrice,
                    onValueChange = { onUpdate(item.copy(unitPrice = it)) },
                    label = { Text("Unit Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            val qty = item.quantity.toDoubleOrNull() ?: 0.0
            val price = item.unitPrice.toDoubleOrNull() ?: 0.0
            if (qty > 0 && price > 0) {
                Text(
                    "Line total: $${formatInvoiceAmount(qty * price)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun formatInvoiceAmount(value: Double): String {
    val intPart = value.toLong()
    val fracPart = ((value - intPart) * 100 + 0.5).toLong()
    return "$intPart.${fracPart.toString().padStart(2, '0')}"
}

private fun formatQty(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
