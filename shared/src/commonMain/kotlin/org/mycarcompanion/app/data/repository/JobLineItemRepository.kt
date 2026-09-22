package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import org.mycarcompanion.app.data.models.JobLineItem
import org.mycarcompanion.app.data.models.JobLineItemInsert

/** Line items on a mechanic job. A DB trigger keeps mechanic_jobs.total_cost equal to their sum. */
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

    suspend fun delete(id: String): Result<Unit> = runCatching {
        table.delete { filter { eq("id", id) } }
    }
}
