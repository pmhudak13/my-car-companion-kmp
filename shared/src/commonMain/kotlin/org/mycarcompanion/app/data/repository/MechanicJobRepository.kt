package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
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

    suspend fun createJob(insert: MechanicJobInsert): Result<MechanicJob> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        jobsTable.insert(insert.copy(mechanicUserId = userId)) {
            select()
        }.decodeSingle<MechanicJob>()
    }

    suspend fun completeJob(jobId: String): Result<Unit> = runCatching {
        jobsTable.update({
            set("status", "completed")
            set("completed_at", kotlinx.datetime.Clock.System.now().toString())
        }) {
            filter { eq("id", jobId) }
        }
    }

    suspend fun getLogsForJob(jobId: String): Result<List<MechanicJobLog>> = runCatching {
        logsTable.select {
            filter { eq("mechanic_job_id", jobId) }
            order("date", Order.DESCENDING)
        }.decodeList<MechanicJobLog>()
    }

    suspend fun addLog(log: MechanicJobLogInsert): Result<MechanicJobLog> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        logsTable.insert(log.copy(mechanicUserId = userId)) {
            select()
        }.decodeSingle<MechanicJobLog>()
    }

    suspend fun deleteLog(logId: String): Result<Unit> = runCatching {
        logsTable.delete {
            filter { eq("id", logId) }
        }
    }

    suspend fun sendInvite(
        jobId: String,
        clientEmail: String,
        clientName: String,
        mechanicName: String,
        vehicleInfo: String,
    ): Result<Unit> = runCatching {
        client.auth.currentSessionOrNull() ?: error("Session expired — please sign out and sign back in")
        val body = buildJsonObject {
            put("jobId", jobId)
            put("clientEmail", clientEmail)
            put("clientName", clientName)
            put("mechanicName", mechanicName)
            put("vehicleInfo", vehicleInfo)
        }
        client.functions.invoke(
            function = "send-mechanic-invite",
            body = body,
        )
    }
}
