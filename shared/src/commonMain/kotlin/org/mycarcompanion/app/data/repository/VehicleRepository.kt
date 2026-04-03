package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import org.mycarcompanion.app.data.models.Vehicle

class VehicleRepository(private val client: SupabaseClient) {

    private val table get() = client.postgrest["vehicles"]

    suspend fun getVehicles(): Result<List<Vehicle>> = runCatching {
        table.select {
            order("created_at", Order.DESCENDING)
        }.decodeList<Vehicle>()
    }

    suspend fun getVehicle(id: String): Result<Vehicle> = runCatching {
        table.select {
            filter { eq("id", id) }
        }.decodeSingle<Vehicle>()
    }

    suspend fun addVehicle(vehicle: Vehicle): Result<Vehicle> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id
            ?: error("Not authenticated")
        table.insert(vehicle.copy(ownerId = userId)) {
            select()
        }.decodeSingle<Vehicle>()
    }

    suspend fun updateVehicle(vehicle: Vehicle): Result<Vehicle> = runCatching {
        table.update(vehicle) {
            select()
            filter { eq("id", vehicle.id) }
        }.decodeSingle<Vehicle>()
    }

    suspend fun deleteVehicle(id: String): Result<Unit> = runCatching {
        table.delete {
            filter { eq("id", id) }
        }
    }
}
