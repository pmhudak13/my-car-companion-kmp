package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import org.mycarcompanion.app.data.models.MechanicProfile

class MechanicRepository(private val client: SupabaseClient) {

    private val publicView get() = client.postgrest["mechanic_profiles_public"]

    suspend fun getVerifiedMechanics(): Result<List<MechanicProfile>> = runCatching {
        publicView.select {
            filter {
                eq("verification_status", "verified")
                eq("is_available", true)
            }
            order("rating", Order.DESCENDING)
        }.decodeList<MechanicProfile>()
    }

    suspend fun getMechanic(userId: String): Result<MechanicProfile> = runCatching {
        publicView.select {
            filter { eq("user_id", userId) }
        }.decodeSingle<MechanicProfile>()
    }
}
