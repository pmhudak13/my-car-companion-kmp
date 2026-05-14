package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
