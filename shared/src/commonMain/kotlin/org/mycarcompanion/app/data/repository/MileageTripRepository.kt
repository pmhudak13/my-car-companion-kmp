package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Clock
import org.mycarcompanion.app.data.models.MileageTrip

class MileageTripRepository(private val client: SupabaseClient) {

    private val table get() = client.postgrest["mileage_trips"]

    suspend fun getTrips(vehicleId: String? = null): Result<List<MileageTrip>> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        table.select {
            filter {
                eq("user_id", userId)
                if (vehicleId != null) eq("vehicle_id", vehicleId)
            }
            order("started_at", Order.DESCENDING)
        }.decodeList<MileageTrip>()
    }

    suspend fun startTrip(
        vehicleId: String?,
        purpose: String,
        notes: String?,
        startLat: Double?,
        startLng: Double?,
    ): Result<MileageTrip> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id
            ?: error("Not authenticated")
        val trip = MileageTrip(
            userId = userId,
            vehicleId = vehicleId,
            purpose = purpose,
            notes = notes,
            startLat = startLat,
            startLng = startLng,
        )
        table.insert(trip) {
            select()
        }.decodeSingle<MileageTrip>()
    }

    suspend fun endTrip(
        tripId: String,
        distanceMiles: Double,
        endLat: Double?,
        endLng: Double?,
    ): Result<MileageTrip> = runCatching {
        table.update({
            set("distance_miles", distanceMiles)
            set("end_lat", endLat)
            set("end_lng", endLng)
            set("ended_at", Clock.System.now().toString())
        }) {
            filter {
                eq("id", tripId)
                isNull("ended_at")
            }
            select()
        }.decodeSingle<MileageTrip>()
    }

    suspend fun deleteTrip(id: String): Result<Unit> = runCatching {
        table.delete {
            filter { eq("id", id) }
        }
    }
}
