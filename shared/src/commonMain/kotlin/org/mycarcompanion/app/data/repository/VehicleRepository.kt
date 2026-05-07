package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.mycarcompanion.app.data.models.Reminder
import org.mycarcompanion.app.data.models.Vehicle
import org.mycarcompanion.app.data.models.reminderTypeLabels

class VehicleRepository(private val client: SupabaseClient) {

    private val table get() = client.postgrest["vehicles"]

    suspend fun getVehicles(): Result<List<Vehicle>> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id
            ?: error("Not authenticated")
        table.select {
            filter { eq("owner_id", userId) }
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
        val updated = table.update({
            set("make", vehicle.make)
            set("model", vehicle.model)
            set("year", vehicle.year)
            set("color", vehicle.color)
            set("license_plate", vehicle.licensePlate)
            set("vin", vehicle.vin)
            set("odometer", vehicle.odometer)
            set("unit", vehicle.unit)
            set("image_url", vehicle.imageUrl)
        }) {
            select()
            filter { eq("id", vehicle.id) }
        }.decodeSingle<Vehicle>()
        checkMileageReminders(vehicle.id, vehicle.odometer)
        updated
    }

    private suspend fun checkMileageReminders(vehicleId: String, odometer: Int) {
        try {
            val userId = client.auth.currentUserOrNull()?.id ?: return
            val reminders = client.postgrest["reminders"].select {
                filter {
                    eq("vehicle_id", vehicleId)
                    eq("is_active", true)
                }
            }.decodeList<Reminder>()

            // Guard: skip push if no active session (SDK needs auth to call the function)
            client.auth.currentSessionOrNull() ?: return

            reminders.forEach { reminder ->
                val dueMileage = reminder.nextDueMileage ?: return@forEach
                if (odometer >= dueMileage) {
                    val label = reminderTypeLabels[reminder.type] ?: reminder.customName ?: "Maintenance"
                    client.functions.invoke(
                        "send-push-notification",
                        body = buildJsonObject {
                            put("recipient_id", userId)
                            put("title", "$label Reminder")
                            put("body", "Your $label is due at $dueMileage miles.")
                            put("type", reminder.type)
                        },
                    )
                }
            }
        } catch (_: Exception) {
            // Best-effort — never fail the vehicle update
        }
    }

    suspend fun deleteVehicle(id: String): Result<Unit> = runCatching {
        table.delete {
            filter { eq("id", id) }
        }
    }
}
