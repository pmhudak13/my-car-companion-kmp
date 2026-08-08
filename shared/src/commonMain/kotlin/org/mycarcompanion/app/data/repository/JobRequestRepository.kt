package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import org.mycarcompanion.app.data.models.JobRequest
import org.mycarcompanion.app.data.models.JobRequestInsert

class JobRequestRepository(private val client: SupabaseClient) {

    private val table get() = client.postgrest["job_requests"]

    suspend fun getMyRequests(): Result<List<JobRequest>> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        table.select {
            filter { eq("owner_id", userId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<JobRequest>()
    }

    // RLS restricts this to verified mechanics; owners get nothing back.
    // ponytail: no location filter — every open request, newest first.
    // Add a city filter here when the board gets long enough to scroll.
    suspend fun getOpenRequests(): Result<List<JobRequest>> = runCatching {
        table.select {
            filter { eq("status", "open") }
            order("created_at", Order.DESCENDING)
        }.decodeList<JobRequest>()
    }

    suspend fun postRequest(
        title: String,
        description: String?,
        vehicleLabel: String,
        vehicleId: String?,
        city: String?,
        state: String?,
    ): Result<JobRequest> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        val insert = JobRequestInsert(
            ownerId = userId,
            vehicleId = vehicleId,
            vehicleLabel = vehicleLabel,
            title = title.trim(),
            description = description?.trim()?.ifBlank { null },
            city = city?.trim()?.ifBlank { null },
            state = state?.trim()?.ifBlank { null },
        )
        table.insert(insert) { select() }.decodeSingle<JobRequest>()
    }

    suspend fun closeRequest(id: String): Result<Unit> = runCatching {
        table.update({ set("status", "closed") }) {
            filter { eq("id", id) }
        }
        Unit
    }
}
