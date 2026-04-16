package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.mycarcompanion.app.data.models.MechanicInvoice
import org.mycarcompanion.app.data.models.MechanicInvoiceInsert
import org.mycarcompanion.app.data.models.MechanicInvoiceItem
import org.mycarcompanion.app.data.models.MechanicInvoiceItemInsert

class MechanicInvoiceRepository(private val client: SupabaseClient) {

    private val invoicesTable get() = client.postgrest["mechanic_invoices"]
    private val itemsTable get() = client.postgrest["mechanic_invoice_items"]

    suspend fun getInvoicesForJob(jobId: String): Result<List<MechanicInvoice>> = runCatching {
        invoicesTable.select {
            filter { eq("mechanic_job_id", jobId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<MechanicInvoice>()
    }

    suspend fun getInvoiceById(invoiceId: String): Result<MechanicInvoice> = runCatching {
        invoicesTable.select {
            filter { eq("id", invoiceId) }
        }.decodeSingle<MechanicInvoice>()
    }

    suspend fun getItemsForInvoice(invoiceId: String): Result<List<MechanicInvoiceItem>> = runCatching {
        itemsTable.select {
            filter { eq("invoice_id", invoiceId) }
        }.decodeList<MechanicInvoiceItem>()
    }

    /**
     * Creates the invoice + all line items in one go.
     * Returns the created invoice.
     */
    suspend fun createInvoice(
        insert: MechanicInvoiceInsert,
        items: List<MechanicInvoiceItemInsert>,
    ): Result<MechanicInvoice> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        val invoice = invoicesTable.insert(insert.copy(mechanicUserId = userId)) {
            select()
        }.decodeSingle<MechanicInvoice>()

        if (items.isNotEmpty()) {
            itemsTable.insert(items.map { it.copy(invoiceId = invoice.id) })
        }
        invoice
    }

    /**
     * Marks the invoice as sent.
     */
    suspend fun sendInvoice(invoiceId: String): Result<MechanicInvoice> = runCatching {
        invoicesTable.update({
            set("status", "sent")
            set("updated_at", kotlinx.datetime.Clock.System.now().toString())
        }) {
            filter { eq("id", invoiceId) }
            select()
        }.decodeSingle<MechanicInvoice>()
    }

    /**
     * Marks the invoice as paid (basic tier — mechanic manually marks it paid).
     */
    suspend fun markPaid(invoiceId: String): Result<MechanicInvoice> = runCatching {
        val now = kotlinx.datetime.Clock.System.now().toString()
        invoicesTable.update({
            set("status", "paid")
            set("paid_at", now)
            set("updated_at", now)
        }) {
            filter { eq("id", invoiceId) }
            select()
        }.decodeSingle<MechanicInvoice>()
    }

    /**
     * Stripe tier: calls an Edge Function that creates a Stripe Payment Link
     * and stores the URL back on the invoice.
     */
    suspend fun createStripePaymentLink(invoiceId: String): Result<String> = runCatching {
        client.auth.currentUserOrNull() ?: error("Not authenticated")
        val response = client.functions.invoke(
            function = "create-invoice-payment-link",
            body = buildJsonObject { put("invoiceId", invoiceId) },
        )
        // Edge Function returns { paymentLinkUrl: "https://buy.stripe.com/..." }
        val json = Json.parseToJsonElement(response.body<String>())
        json.jsonObject["paymentLinkUrl"]?.jsonPrimitive?.content
            ?: error("No paymentLinkUrl in response")
    }

    suspend fun cancelInvoice(invoiceId: String): Result<Unit> = runCatching {
        invoicesTable.update({
            set("status", "cancelled")
            set("updated_at", kotlinx.datetime.Clock.System.now().toString())
        }) {
            filter { eq("id", invoiceId) }
        }
    }

    /** Client-side: get invoices addressed to the current user */
    suspend fun getMyClientInvoices(): Result<List<MechanicInvoice>> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        invoicesTable.select {
            filter { eq("client_user_id", userId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<MechanicInvoice>()
    }
}
