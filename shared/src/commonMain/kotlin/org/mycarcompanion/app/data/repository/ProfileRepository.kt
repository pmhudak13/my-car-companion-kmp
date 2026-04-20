package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.datetime.Clock
import org.mycarcompanion.app.data.models.AdminUserEntry
import org.mycarcompanion.app.data.models.GiftedSubscription
import org.mycarcompanion.app.data.models.MechanicProfile
import org.mycarcompanion.app.data.models.MechanicProfileInsert
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

    suspend fun getMyMechanicProfile(): Result<MechanicProfile?> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: return Result.success(null)
        client.postgrest["mechanic_profiles"].select {
            filter { eq("user_id", userId) }
        }.decodeList<MechanicProfile>().firstOrNull()
    }

    suspend fun getAllMechanicProfiles(): Result<List<MechanicProfile>> = runCatching {
        client.postgrest["mechanic_profiles"].select {
            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
        }.decodeList<MechanicProfile>()
    }

    suspend fun approveMechanic(userId: String): Result<Unit> = runCatching {
        val now = Clock.System.now().toString()
        client.postgrest["mechanic_profiles"].update({
            set("verification_status", "verified")
            set("verified_at", now)
        }) {
            filter { eq("user_id", userId) }
        }
        // Insert role, ignore if it already exists
        runCatching {
            client.postgrest["user_roles"].insert(
                mapOf("user_id" to userId, "role" to "mechanic")
            )
        }
        Unit
    }

    suspend fun convertToMechanic(userId: String): Result<Unit> = runCatching {
        val now = Clock.System.now().toString()
        // Insert a minimal profile if one doesn't exist yet (ignore if it does)
        runCatching {
            client.postgrest["mechanic_profiles"].insert(
                MechanicProfileInsert(
                    userId = userId,
                    shopName = "My Shop",
                    shopType = "general",
                    updatedAt = now,
                )
            )
        }
        // Mark verified and grant mechanic role
        approveMechanic(userId).getOrThrow()
    }

    suspend fun rejectMechanic(userId: String): Result<Unit> = runCatching {
        client.postgrest["mechanic_profiles"].update({
            set("verification_status", "rejected")
        }) {
            filter { eq("user_id", userId) }
        }
        // Remove mechanic role so they no longer get the mechanic dashboard
        runCatching {
            client.postgrest["user_roles"].delete {
                filter {
                    eq("user_id", userId)
                    eq("role", "mechanic")
                }
            }
        }
        Unit
    }

    suspend fun revokeMechanicRole(userId: String): Result<Unit> = runCatching {
        client.postgrest["user_roles"].delete {
            filter {
                eq("user_id", userId)
                eq("role", "mechanic")
            }
        }
        runCatching {
            client.postgrest["mechanic_profiles"].update({
                set("verification_status", "rejected")
            }) {
                filter { eq("user_id", userId) }
            }
        }
        Unit
    }

    suspend fun updateProfile(firstName: String, lastName: String): Result<Unit> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id
            ?: error("Not authenticated")
        profiles.update({
            set("first_name", firstName)
            set("last_name", lastName)
        }) {
            filter { eq("user_id", userId) }
        }
    }

    suspend fun upsertMechanicProfile(
        shopName: String,
        shopType: String,
        bio: String?,
        city: String?,
        state: String?,
        yearsExperience: Int?,
        hourlyRate: Double?,
        googlePlaceUrl: String? = null,
        yelpUrl: String? = null,
    ): Result<Unit> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id
            ?: error("Not authenticated")
        val now = Clock.System.now().toString()
        val existing = getMyMechanicProfile().getOrNull()
        if (existing != null) {
            client.postgrest["mechanic_profiles"].update({
                set("shop_name", shopName)
                set("shop_type", shopType)
                set("bio", bio)
                set("city", city)
                set("state", state)
                set("years_experience", yearsExperience)
                set("hourly_rate", hourlyRate)
                set("google_place_url", googlePlaceUrl)
                set("yelp_url", yelpUrl)
                set("updated_at", now)
            }) {
                filter { eq("user_id", userId) }
            }
        } else {
            client.postgrest["mechanic_profiles"].insert(
                MechanicProfileInsert(
                    userId = userId,
                    shopName = shopName,
                    shopType = shopType,
                    bio = bio,
                    city = city,
                    state = state,
                    yearsExperience = yearsExperience,
                    hourlyRate = hourlyRate,
                    googlePlaceUrl = googlePlaceUrl,
                    yelpUrl = yelpUrl,
                    updatedAt = now,
                )
            )
        }
    }
}
