package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import org.mycarcompanion.app.data.models.MaintenanceLog

class MaintenanceRepository(private val client: SupabaseClient) {

    private val table get() = client.postgrest["maintenance_logs"]

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

    suspend fun deleteLog(id: String): Result<Unit> = runCatching {
        table.delete {
            filter { eq("id", id) }
        }
    }
}
