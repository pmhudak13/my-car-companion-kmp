package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import org.mycarcompanion.app.data.models.NotificationPreferences

class NotificationPreferencesRepository(private val client: SupabaseClient) {

    private val table get() = client.postgrest["notification_preferences"]

    suspend fun getPreferences(): Result<NotificationPreferences> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        table.select {
            filter { eq("user_id", userId) }
        }.decodeSingleOrNull<NotificationPreferences>()
            ?: NotificationPreferences(userId = userId)
    }

    suspend fun savePreferences(prefs: NotificationPreferences): Result<Unit> = runCatching {
        table.upsert(prefs) {
            onConflict = "user_id"
        }
        Unit
    }
}
