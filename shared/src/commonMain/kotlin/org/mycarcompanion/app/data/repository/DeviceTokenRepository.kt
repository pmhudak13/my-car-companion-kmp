package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import org.mycarcompanion.app.data.models.DeviceToken

class DeviceTokenRepository(private val client: SupabaseClient) {

    private val table get() = client.postgrest["device_tokens"]

    suspend fun upsertToken(token: String, platform: String): Result<Unit> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: return Result.failure(Exception("Not authenticated"))
        table.upsert(DeviceToken(userId = userId, token = token, platform = platform)) {
            onConflict = "user_id,platform"
        }
        Unit
    }

    suspend fun deleteToken(platform: String): Result<Unit> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: return Result.failure(Exception("Not authenticated"))
        table.delete {
            filter {
                eq("user_id", userId)
                eq("platform", platform)
            }
        }
        Unit
    }
}
