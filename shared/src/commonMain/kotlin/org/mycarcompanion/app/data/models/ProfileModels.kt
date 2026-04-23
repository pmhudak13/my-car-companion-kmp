package org.mycarcompanion.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val email: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("subscription_tier") val subscriptionTier: String = "free",
    @SerialName("is_premium") val isPremium: Boolean = false,
    @SerialName("is_mechanic_pro") val isMechanicPro: Boolean = false,
)

@Serializable
data class UserRole(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val role: String = "owner",
)

@Serializable
data class GiftedSubscription(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("gifted_by") val giftedBy: String = "",
    val reason: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("created_at") val createdAt: String = "",
)

data class AdminUserEntry(
    val userId: String,
    val email: String,
    val isPremium: Boolean,
    val subscriptionTier: String,
    val role: String,
)

@Serializable
data class UserRoleInsert(
    @SerialName("user_id") val userId: String,
    val role: String,
)
