package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.mycarcompanion.app.data.models.ImportedRecord
import org.mycarcompanion.app.data.models.MechanicJob
import org.mycarcompanion.app.data.models.MechanicJobInsert
import org.mycarcompanion.app.data.models.MechanicJobLog
import org.mycarcompanion.app.data.models.MechanicJobLogInsert

class MechanicJobRepository(private val client: SupabaseClient) {

    private val jobsTable get() = client.postgrest["mechanic_jobs"]
    private val logsTable get() = client.postgrest["mechanic_job_logs"]

    suspend fun getMyJobs(statusFilter: String? = null): Result<List<MechanicJob>> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        jobsTable.select {
            filter {
                eq("mechanic_user_id", userId)
                if (statusFilter != null) eq("status", statusFilter)
            }
            order("created_at", Order.DESCENDING)
        }.decodeList<MechanicJob>()
    }

    suspend fun getJobById(jobId: String): Result<MechanicJob?> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        jobsTable.select {
            filter {
                eq("id", jobId)
                eq("mechanic_user_id", userId)
            }
        }.decodeList<MechanicJob>().firstOrNull()
    }

    suspend fun createJob(insert: MechanicJobInsert): Result<MechanicJob> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        jobsTable.insert(insert.copy(mechanicUserId = userId)) {
            select()
        }.decodeSingle<MechanicJob>()
    }

    suspend fun completeJob(jobId: String, clientEmail: String? = null): Result<Unit> = runCatching {
        jobsTable.update({
            set("status", "completed")
            set("completed_at", Clock.System.now().toString())
        }) {
            filter { eq("id", jobId) }
        }
        triggerJobUpdatePush(clientEmail, "Job Completed", "Your mechanic has marked your job as complete.")
    }

    suspend fun linkJobByVin(jobId: String, vin: String): Result<Unit> = runCatching {
        client.postgrest.rpc(
            "link_mechanic_job_by_vin",
            buildJsonObject {
                put("p_job_id", jobId)
                put("p_vin", vin)
            },
        )
    }

    suspend fun updateLog(
        logId: String,
        category: String,
        description: String,
        date: String,
        mileage: Int,
        cost: Double?,
        notes: String?,
        editNotes: String?,
        clientEmail: String? = null,
    ): Result<MechanicJobLog> = runCatching {
        val now = Clock.System.now().toString()
        val result = logsTable.update({
            set("category", category)
            set("description", description)
            set("date", date)
            set("mileage", mileage)
            set("cost", cost)
            set("notes", notes)
            set("updated_at", now)
            set("edit_notes", editNotes)
        }) {
            filter { eq("id", logId) }
            select()
        }.decodeSingle<MechanicJobLog>()
        triggerJobUpdatePush(clientEmail, "Job Updated", "Your mechanic has updated a service record.")
        result
    }

    suspend fun getLogsForJob(jobId: String): Result<List<MechanicJobLog>> = runCatching {
        logsTable.select {
            filter { eq("mechanic_job_id", jobId) }
            order("date", Order.DESCENDING)
        }.decodeList<MechanicJobLog>()
    }

    suspend fun addLog(log: MechanicJobLogInsert, clientEmail: String? = null): Result<MechanicJobLog> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        val result = logsTable.insert(log.copy(mechanicUserId = userId)) {
            select()
        }.decodeSingle<MechanicJobLog>()
        triggerJobUpdatePush(clientEmail, "Job Updated", "Your mechanic has added a new update to your job.")
        triggerJobUpdateEmail(clientEmail, "Job Updated", "Your mechanic has added a new update to your job.")
        result
    }

    suspend fun deleteLog(logId: String): Result<Unit> = runCatching {
        logsTable.delete {
            filter { eq("id", logId) }
        }
    }

    private suspend fun triggerJobUpdatePush(clientEmail: String?, title: String, body: String) {
        if (clientEmail == null) return
        try {
            client.functions.invoke(
                "send-push-notification",
                body = buildJsonObject {
                    put("client_email", clientEmail)
                    put("title", title)
                    put("body", body)
                    put("type", "mechanic_update")
                },
            )
        } catch (_: Exception) {
            // Best-effort
        }
    }

    private suspend fun triggerJobUpdateEmail(clientEmail: String?, title: String, body: String) {
        if (clientEmail == null) return
        try {
            client.functions.invoke(
                "send-email-notification",
                body = buildJsonObject {
                    put("client_email", clientEmail)
                    put("title", title)
                    put("body", body)
                    put("type", "mechanic_update")
                },
            )
        } catch (_: Exception) {
            // Best-effort
        }
    }

    /** Inserts multiple job logs in one batch and returns the count saved. */
    suspend fun addLogsInBulk(
        logs: List<MechanicJobLogInsert>,
        clientEmail: String? = null,
    ): Result<Int> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        val withUser = logs.map { it.copy(mechanicUserId = userId) }
        logsTable.insert(withUser)
        if (!clientEmail.isNullOrBlank() && logs.isNotEmpty()) {
            triggerJobUpdatePush(
                clientEmail,
                "Service Records Added",
                "${logs.size} service record${if (logs.size != 1) "s" else ""} have been added to your vehicle history.",
            )
        }
        logs.size
    }

    @Serializable
    private data class ParsedRecordDto(
        val date: String = "",
        val category: String = "",
        val description: String = "",
        val mileage: Int = 0,
        val cost: Double? = null,
        val notes: String? = null,
        @SerialName("isValid") val isValid: Boolean = true,
    )

    @Serializable
    private data class ParseInvoiceResponse(val records: List<ParsedRecordDto> = emptyList())

    /** Sends an invoice image to the AI edge function and returns extracted records. */
    suspend fun parseInvoiceRecords(
        imageBase64: String,
        mimeType: String,
        jobId: String,
    ): Result<List<ImportedRecord>> = runCatching {
        val response = client.functions.invoke(
            function = "parse-invoice-records",
            body = buildJsonObject {
                put("image_base64", imageBase64)
                put("mime_type", mimeType)
                put("job_id", jobId)
            },
        )
        val body = response.bodyAsText()
        val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
        val serverError = (json?.get("error") as? JsonPrimitive)?.content
        if (serverError != null) error(serverError)
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<ParseInvoiceResponse>(body)
        parsed.records.map { dto ->
            ImportedRecord(
                date = dto.date,
                category = dto.category,
                description = dto.description,
                mileage = dto.mileage,
                cost = dto.cost,
                notes = dto.notes,
                isValid = dto.isValid && dto.description.isNotBlank(),
            )
        }
    }

    suspend fun sendInvite(
        jobId: String,
        clientEmail: String,
        clientName: String,
        mechanicName: String,
        vehicleInfo: String,
    ): Result<Unit> = runCatching {
        client.auth.currentSessionOrNull()
            ?: error("Session expired — please sign out and sign back in")
        client.functions.invoke(
            function = "send-mechanic-invite",
            body = buildJsonObject {
                put("jobId", jobId)
                put("clientEmail", clientEmail)
                put("clientName", clientName)
                put("mechanicName", mechanicName)
                put("vehicleInfo", vehicleInfo)
            },
        )
    }
}
