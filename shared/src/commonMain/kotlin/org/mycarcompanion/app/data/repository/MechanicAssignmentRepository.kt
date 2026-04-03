package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import org.mycarcompanion.app.data.models.MechanicAssignment

class MechanicAssignmentRepository(private val client: SupabaseClient) {

    private val table get() = client.postgrest["mechanic_assignments"]

    suspend fun getAssignmentsForVehicle(vehicleId: String): Result<List<MechanicAssignment>> = runCatching {
        table.select {
            filter {
                eq("vehicle_id", vehicleId)
                eq("status", "active")
            }
            order("assigned_at", Order.DESCENDING)
        }.decodeList<MechanicAssignment>()
    }

    suspend fun createAssignment(vehicleId: String, mechanicUserId: String, notes: String? = null): Result<MechanicAssignment> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id
            ?: error("Not authenticated")
        val assignment = MechanicAssignment(
            vehicleId = vehicleId,
            mechanicUserId = mechanicUserId,
            assignedBy = userId,
            notes = notes,
        )
        table.insert(assignment) {
            select()
        }.decodeSingle<MechanicAssignment>()
    }

    suspend fun revokeAssignment(id: String): Result<Unit> = runCatching {
        table.update({
            set("status", "revoked")
        }) {
            filter { eq("id", id) }
        }
    }
}
