package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import org.mycarcompanion.app.data.models.AdminUserEntry
import org.mycarcompanion.app.data.models.GiftedSubscription
import org.mycarcompanion.app.data.models.UserProfile
import org.mycarcompanion.app.data.models.UserRole

class ProfileRepository(private val client: SupabaseClient) {

    private val profiles get() = client.postgrest["profiles"]
    private val roles get() = client.postgrest["user_roles"]
    private val gifts get() = client.postgrest["gifted_subscriptions"]

    suspend fun hasRole(role: String): Result<Boolean> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: return Result.success(false)
        val result = roles.select {
            filter {
                eq("user_id", userId)
                eq("role", role)
            }
        }.decodeList<UserRole>()
        result.isNotEmpty()
    }

    suspend fun getMyProfile(): Result<UserProfile?> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: return Result.success(null)
        profiles.select {
            filter { eq("user_id", userId) }
        }.decodeList<UserProfile>().firstOrNull()
    }

    suspend fun getAllUsers(): Result<List<AdminUserEntry>> = runCatching {
        val allProfiles = profiles.select().decodeList<UserProfile>()
        val allRoles = roles.select().decodeList<UserRole>()
        val roleMap = allRoles.groupBy { it.userId }

        allProfiles.map { profile ->
            val userRoles = roleMap[profile.userId] ?: emptyList()
            val topRole = when {
                userRoles.any { it.role == "admin" } -> "admin"
                userRoles.any { it.role == "mechanic" } -> "mechanic"
                else -> "owner"
            }
            AdminUserEntry(
                userId = profile.userId,
                email = profile.email ?: "",
                isPremium = profile.isPremium,
                subscriptionTier = profile.subscriptionTier,
                role = topRole,
            )
        }
    }

    suspend fun giftPremium(userId: String, reason: String?): Result<Unit> = runCatching {
        val adminId = client.auth.currentUserOrNull()?.id
            ?: error("Not authenticated")

        // Insert gifted subscription record
        gifts.insert(
            GiftedSubscription(
                userId = userId,
                giftedBy = adminId,
                reason = reason,
            )
        )

        // Update profile to premium
        profiles.update({
            set("is_premium", true)
            set("subscription_tier", "premium")
        }) {
            filter { eq("user_id", userId) }
        }
    }

    suspend fun revokePremium(userId: String): Result<Unit> = runCatching {
        // Remove gifted subscription records
        gifts.delete {
            filter { eq("user_id", userId) }
        }

        // Update profile back to free
        profiles.update({
            set("is_premium", false)
            set("subscription_tier", "free")
        }) {
            filter { eq("user_id", userId) }
        }
    }
}
