package org.mycarcompanion.app.data.repository

// DB migrations required:
// ALTER TABLE maintenance_logs ADD COLUMN IF NOT EXISTS photo_urls TEXT[] DEFAULT '{}';
// ALTER TABLE maintenance_logs ADD COLUMN IF NOT EXISTS approval_status TEXT DEFAULT NULL;
// Existing records (approval_status IS NULL) are treated as approved for backwards compatibility.

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import org.mycarcompanion.app.data.models.MaintenanceLog

class MaintenanceRepository(private val client: SupabaseClient) {

    private val table get() = client.postgrest["maintenance_logs"]

    /** Returns approved logs (approval_status IS NULL or 'approved') for a vehicle. */
    suspend fun getLogsForVehicle(vehicleId: String): Result<List<MaintenanceLog>> = runCatching {
        table.select {
            filter { eq("vehicle_id", vehicleId) }
            order("date", Order.DESCENDING)
        }.decodeList<MaintenanceLog>().filter { it.approvalStatus != "pending" }
    }

    /** Returns logs awaiting owner approval (mechanic-submitted, not yet accepted). */
    suspend fun getPendingLogsForVehicle(vehicleId: String): Result<List<MaintenanceLog>> = runCatching {
        table.select {
            filter { eq("vehicle_id", vehicleId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<MaintenanceLog>().filter { it.approvalStatus == "pending" }
    }

    suspend fun addLog(log: MaintenanceLog): Result<MaintenanceLog> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id
            ?: error("Not authenticated")
        table.insert(log.copy(createdByUserId = userId)) {
            select()
        }.decodeSingle<MaintenanceLog>()
    }

    /** Owner approves a mechanic-submitted record. */
    suspend fun approveLog(logId: String): Result<Unit> = runCatching {
        table.update({
            set("approval_status", "approved")
        }) {
            filter { eq("id", logId) }
        }
    }

    /** Owner rejects (deletes) a mechanic-submitted record. */
    suspend fun rejectLog(logId: String): Result<Unit> = runCatching {
        table.delete {
            filter { eq("id", logId) }
        }
    }

    suspend fun deleteLog(id: String): Result<Unit> = runCatching {
        table.delete {
            filter { eq("id", id) }
        }
    }
}
