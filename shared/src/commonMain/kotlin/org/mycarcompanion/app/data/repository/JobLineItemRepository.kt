package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import org.mycarcompanion.app.data.models.CannedJob
import org.mycarcompanion.app.data.models.CannedJobInsert
import org.mycarcompanion.app.data.models.CannedLine
import org.mycarcompanion.app.data.models.JobLineItem
import org.mycarcompanion.app.data.models.JobLineItemInsert

/** Line items on a mechanic job, plus the mechanic's canned jobs. A DB trigger keeps mechanic_jobs.total_cost equal to their sum. */
class JobLineItemRepository(private val client: SupabaseClient) {

    private val table get() = client.postgrest["mechanic_job_line_items"]

    suspend fun getForJobs(jobIds: List<String>): Result<List<JobLineItem>> = runCatching {
        if (jobIds.isEmpty()) return@runCatching emptyList()
        table.select {
            filter { isIn("mechanic_job_id", jobIds) }
            order("created_at", Order.ASCENDING)
        }.decodeList<JobLineItem>()
    }

    suspend fun add(insert: JobLineItemInsert): Result<JobLineItem> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        table.insert(insert.copy(mechanicUserId = userId)) { select() }.decodeSingle<JobLineItem>()
    }

    suspend fun addAll(inserts: List<JobLineItemInsert>): Result<List<JobLineItem>> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        table.insert(inserts.map { it.copy(mechanicUserId = userId) }) { select() }.decodeList<JobLineItem>()
    }

    suspend fun delete(id: String): Result<Unit> = runCatching {
        table.delete { filter { eq("id", id) } }
    }

    // ── Canned jobs (RLS keeps them private to the mechanic) ──

    private val cannedTable get() = client.postgrest["mechanic_canned_jobs"]

    suspend fun getCannedJobs(): Result<List<CannedJob>> = runCatching {
        cannedTable.select { order("name", Order.ASCENDING) }.decodeList<CannedJob>()
    }

    suspend fun saveCannedJob(name: String, lines: List<CannedLine>): Result<CannedJob> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        cannedTable.insert(CannedJobInsert(userId, name, lines)) { select() }.decodeSingle<CannedJob>()
    }

    suspend fun deleteCannedJob(id: String): Result<Unit> = runCatching {
        cannedTable.delete { filter { eq("id", id) } }
    }
}
