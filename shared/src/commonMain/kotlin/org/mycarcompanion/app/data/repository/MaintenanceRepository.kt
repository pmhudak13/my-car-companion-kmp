package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.mycarcompanion.app.data.models.ImportedRecord
import org.mycarcompanion.app.data.models.MaintenanceLog
import kotlin.time.Duration.Companion.hours

/** What an owner's invoice scan found: records to review, a VIN if printed, and the stored scan's id. */
data class InvoiceScanResult(val records: List<ImportedRecord>, val vin: String?, val scanId: String?)

@Serializable
private data class ScanRecordDto(
    val date: String = "",
    val category: String = "Other",
    val description: String = "",
    val mileage: Int = 0,
    val cost: Double? = null,
    val notes: String? = null,
    val isValid: Boolean = true,
)

@Serializable
private data class ScanResponse(
    val records: List<ScanRecordDto> = emptyList(),
    val vin: String? = null,
    @SerialName("scan_id") val scanId: String? = null,
)

@Serializable
private data class ScanFile(@SerialName("storage_path") val storagePath: String? = null)

class MaintenanceRepository(private val client: SupabaseClient) {

    private val table get() = client.postgrest["maintenance_logs"]
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getLogsForVehicle(vehicleId: String): Result<List<MaintenanceLog>> = runCatching {
        table.select {
            filter { eq("vehicle_id", vehicleId) }
            order("date", Order.DESCENDING)
        }.decodeList<MaintenanceLog>()
    }

    suspend fun addLog(log: MaintenanceLog): Result<MaintenanceLog> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id
            ?: error("Not authenticated")
        table.insert(log.copy(createdByUserId = userId)) {
            select()
        }.decodeSingle<MaintenanceLog>()
    }

    /** One insert for a whole scanned invoice, so it saves all or nothing. */
    suspend fun addLogs(logs: List<MaintenanceLog>): Result<Int> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        table.insert(logs.map { it.copy(createdByUserId = userId) })
        logs.size
    }

    suspend fun deleteLog(id: String): Result<Unit> = runCatching {
        table.delete {
            filter { eq("id", id) }
        }
    }

    /** Owner scan of an old invoice or service record (PDF or photo). Free accounts are capped monthly server-side. */
    suspend fun scanInvoice(vehicleId: String, base64: String, mimeType: String, fileName: String): Result<InvoiceScanResult> = runCatching {
        val body = client.functions.invoke(
            "parse-invoice-records",
            body = buildJsonObject {
                put("image_base64", base64)
                put("mime_type", mimeType)
                put("vehicle_id", vehicleId)
                put("file_name", fileName)
            },
        ).bodyAsText()
        val serverError = (runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()?.get("error") as? JsonPrimitive)?.content
        if (serverError != null) error(serverError)
        val r = json.decodeFromString<ScanResponse>(body)
        InvoiceScanResult(
            records = r.records.map {
                ImportedRecord(it.date, it.category, it.description, it.mileage, it.cost, it.notes, it.isValid)
            },
            vin = r.vin,
            scanId = r.scanId,
        )
    }.recoverCatching { e ->
        // non-2xx arrives as an exception carrying the function's JSON error
        val raw = e.message.orEmpty()
        error(Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1) ?: raw.ifBlank { "Could not scan that file" })
    }

    /** Short-lived link to the invoice a record was scanned from, or null if it wasn't stored. */
    suspend fun invoiceUrl(scanId: String): String? = runCatching {
        val path = client.postgrest["owner_invoice_scans"].select { filter { eq("id", scanId) } }
            .decodeSingleOrNull<ScanFile>()?.storagePath ?: return null
        client.storage["maintenance-attachments"].createSignedUrl(path, 1.hours)
    }.getOrNull()
}
