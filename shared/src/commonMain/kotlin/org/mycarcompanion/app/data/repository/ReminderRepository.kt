package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import org.mycarcompanion.app.data.models.Reminder

class ReminderRepository(private val client: SupabaseClient) {

    private val table get() = client.postgrest["reminders"]

    suspend fun getRemindersForVehicle(vehicleId: String): Result<List<Reminder>> = runCatching {
        table.select {
            filter { eq("vehicle_id", vehicleId) }
            order("next_due_date", Order.ASCENDING)
        }.decodeList<Reminder>()
    }

    suspend fun addReminder(reminder: Reminder): Result<Reminder> = runCatching {
        table.insert(reminder) {
            select()
        }.decodeSingle<Reminder>()
    }

    suspend fun updateReminder(reminder: Reminder): Result<Reminder> = runCatching {
        table.update(reminder) {
            filter { eq("id", reminder.id) }
            select()
        }.decodeSingle<Reminder>()
    }

    suspend fun deleteReminder(id: String): Result<Unit> = runCatching {
        table.delete {
            filter { eq("id", id) }
        }
    }

    suspend fun getRemindersForVehicles(vehicleIds: List<String>): Result<List<Reminder>> = runCatching {
        if (vehicleIds.isEmpty()) return Result.success(emptyList())
        table.select {
            filter { isIn("vehicle_id", vehicleIds) }
            order("next_due_date", Order.ASCENDING)
        }.decodeList<Reminder>()
    }
}
