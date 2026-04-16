package org.mycarcompanion.app.ui.mechanics

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.mycarcompanion.app.data.models.InvoiceForm
import org.mycarcompanion.app.data.models.InvoiceLineItemForm
import org.mycarcompanion.app.data.models.MechanicInvoice
import org.mycarcompanion.app.data.models.MechanicInvoiceInsert
import org.mycarcompanion.app.data.models.MechanicInvoiceItem
import org.mycarcompanion.app.data.models.MechanicInvoiceItemInsert
import org.mycarcompanion.app.data.repository.MechanicInvoiceRepository

data class MechanicInvoiceState(
    val invoice: MechanicInvoice? = null,
    val items: List<MechanicInvoiceItem> = emptyList(),
    val existingInvoices: List<MechanicInvoice> = emptyList(),
    val form: InvoiceForm = InvoiceForm(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSending: Boolean = false,
    val isCreatingPaymentLink: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val showCreateForm: Boolean = false,
)

class MechanicInvoiceScreenModel(
    private val invoiceRepository: MechanicInvoiceRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(MechanicInvoiceState())
    val state: StateFlow<MechanicInvoiceState> = _state.asStateFlow()

    fun load(jobId: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            invoiceRepository.getInvoicesForJob(jobId)
                .onSuccess { invoices ->
                    _state.value = _state.value.copy(
                        existingInvoices = invoices,
                        isLoading = false,
                        // Auto-open the latest invoice if one exists
                        invoice = invoices.firstOrNull(),
                    )
                    // Load items for the latest invoice
                    invoices.firstOrNull()?.let { loadInvoiceItems(it.id) }
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    private fun loadInvoiceItems(invoiceId: String) {
        screenModelScope.launch {
            invoiceRepository.getItemsForInvoice(invoiceId)
                .onSuccess { items ->
                    _state.value = _state.value.copy(items = items)
                }
        }
    }

    fun selectInvoice(invoice: MechanicInvoice) {
        _state.value = _state.value.copy(invoice = invoice, items = emptyList())
        loadInvoiceItems(invoice.id)
    }

    fun showCreateForm() {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        _state.value = _state.value.copy(
            showCreateForm = true,
            form = InvoiceForm(dueDate = today),
            error = null,
        )
    }

    fun hideCreateForm() {
        _state.value = _state.value.copy(showCreateForm = false, error = null)
    }

    fun updateForm(form: InvoiceForm) {
        _state.value = _state.value.copy(form = form)
    }

    fun addLineItem() {
        val form = _state.value.form
        val newId = (form.items.size + 1).toString()
        _state.value = _state.value.copy(
            form = form.copy(items = form.items + InvoiceLineItemForm(id = newId)),
        )
    }

    fun removeLineItem(id: String) {
        val form = _state.value.form
        if (form.items.size <= 1) return
        _state.value = _state.value.copy(
            form = form.copy(items = form.items.filter { it.id != id }),
        )
    }

    fun updateLineItem(updated: InvoiceLineItemForm) {
        val form = _state.value.form
        _state.value = _state.value.copy(
            form = form.copy(items = form.items.map { if (it.id == updated.id) updated else it }),
        )
    }

    fun createInvoice(jobId: String) {
        val form = _state.value.form
        val itemForms = form.items.filter { it.description.isNotBlank() && it.unitPrice.isNotBlank() }
        if (itemForms.isEmpty()) {
            _state.value = _state.value.copy(error = "Add at least one line item with description and price")
            return
        }

        val taxRate = form.taxRate.toDoubleOrNull() ?: 0.0
        val lineItems = itemForms.mapNotNull { item ->
            val qty = item.quantity.toDoubleOrNull() ?: return@mapNotNull null
            val price = item.unitPrice.toDoubleOrNull() ?: return@mapNotNull null
            Triple(item.description.trim(), qty, price)
        }
        val subtotal = lineItems.sumOf { (_, qty, price) -> qty * price }
        val taxAmount = subtotal * (taxRate / 100.0)
        val total = subtotal + taxAmount

        // Generate invoice number: INV-YYYYMMDD-XXXX
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString().replace("-", "")
        val suffix = (1000..9999).random()
        val invoiceNumber = "INV-$today-$suffix"

        val insert = MechanicInvoiceInsert(
            mechanicJobId = jobId,
            mechanicUserId = "",  // filled by repo
            invoiceNumber = invoiceNumber,
            subtotal = subtotal,
            taxRate = taxRate / 100.0,
            taxAmount = taxAmount,
            total = total,
            notes = form.notes.trim().ifBlank { null },
            dueDate = form.dueDate.trim().ifBlank { null },
        )
        val itemInserts = lineItems.map { (desc, qty, price) ->
            MechanicInvoiceItemInsert(
                invoiceId = "",  // filled by repo
                description = desc,
                quantity = qty,
                unitPrice = price,
                total = qty * price,
            )
        }

        screenModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            invoiceRepository.createInvoice(insert, itemInserts)
                .onSuccess { invoice ->
                    val items = invoiceRepository.getItemsForInvoice(invoice.id).getOrNull() ?: emptyList()
                    _state.value = _state.value.copy(
                        isSaving = false,
                        showCreateForm = false,
                        invoice = invoice,
                        items = items,
                        existingInvoices = listOf(invoice) + _state.value.existingInvoices,
                        successMessage = "Invoice ${invoice.invoiceNumber} created",
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isSaving = false, error = e.message ?: "Failed to create invoice")
                }
        }
    }

    fun sendInvoice() {
        val invoice = _state.value.invoice ?: return
        screenModelScope.launch {
            _state.value = _state.value.copy(isSending = true, error = null)
            invoiceRepository.sendInvoice(invoice.id)
                .onSuccess { updated ->
                    _state.value = _state.value.copy(
                        isSending = false,
                        invoice = updated,
                        existingInvoices = _state.value.existingInvoices.map {
                            if (it.id == updated.id) updated else it
                        },
                        successMessage = "Invoice sent",
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isSending = false, error = e.message ?: "Failed to send invoice")
                }
        }
    }

    fun markPaid() {
        val invoice = _state.value.invoice ?: return
        screenModelScope.launch {
            _state.value = _state.value.copy(error = null)
            invoiceRepository.markPaid(invoice.id)
                .onSuccess { updated ->
                    _state.value = _state.value.copy(
                        invoice = updated,
                        existingInvoices = _state.value.existingInvoices.map {
                            if (it.id == updated.id) updated else it
                        },
                        successMessage = "Invoice marked as paid",
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to mark paid")
                }
        }
    }

    fun createStripePaymentLink() {
        val invoice = _state.value.invoice ?: return
        screenModelScope.launch {
            _state.value = _state.value.copy(isCreatingPaymentLink = true, error = null)
            invoiceRepository.createStripePaymentLink(invoice.id)
                .onSuccess { url ->
                    // Reload invoice so stripePaymentLinkUrl is populated
                    invoiceRepository.getInvoiceById(invoice.id).getOrNull()?.let { updated ->
                        _state.value = _state.value.copy(
                            isCreatingPaymentLink = false,
                            invoice = updated,
                            existingInvoices = _state.value.existingInvoices.map {
                                if (it.id == updated.id) updated else it
                            },
                            successMessage = "Payment link created",
                        )
                    } ?: run {
                        _state.value = _state.value.copy(
                            isCreatingPaymentLink = false,
                            successMessage = "Payment link created: $url",
                        )
                    }
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isCreatingPaymentLink = false,
                        error = e.message ?: "Failed to create payment link",
                    )
                }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(successMessage = null, error = null)
    }
}
