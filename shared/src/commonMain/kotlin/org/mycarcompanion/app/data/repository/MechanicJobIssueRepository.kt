package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Clock
import org.mycarcompanion.app.data.models.MechanicJobIssue
import org.mycarcompanion.app.data.models.MechanicJobIssueInsert

class MechanicJobIssueRepository(private val client: SupabaseClient) {

    private val table get() = client.postgrest["mechanic_job_issues"]

    suspend fun getIssuesForJob(jobId: String): Result<List<MechanicJobIssue>> = runCatching {
        table.select {
            filter { eq("mechanic_job_id", jobId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<MechanicJobIssue>()
    }

    suspend fun getIssuesForVehicleJobs(jobIds: List<String>): Result<List<MechanicJobIssue>> = runCatching {
        if (jobIds.isEmpty()) return@runCatching emptyList()
        table.select {
            filter { isIn("mechanic_job_id", jobIds) }
            order("created_at", Order.DESCENDING)
        }.decodeList<MechanicJobIssue>()
    }

    suspend fun addIssue(insert: MechanicJobIssueInsert): Result<MechanicJobIssue> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        table.insert(insert.copy(mechanicUserId = userId)) {
            select()
        }.decodeSingle<MechanicJobIssue>()
    }

    suspend fun respondToIssue(
        issueId: String,
        approved: Boolean,
        ownerResponse: String?,
    ): Result<MechanicJobIssue> = runCatching {
        table.update({
            set("status", if (approved) "approved" else "declined")
            set("owner_response", ownerResponse)
            set("responded_at", Clock.System.now().toString())
        }) {
            filter { eq("id", issueId) }
            select()
        }.decodeSingle<MechanicJobIssue>()
    }

    suspend fun deleteIssue(issueId: String): Result<Unit> = runCatching {
        table.delete { filter { eq("id", issueId) } }
    }
}
