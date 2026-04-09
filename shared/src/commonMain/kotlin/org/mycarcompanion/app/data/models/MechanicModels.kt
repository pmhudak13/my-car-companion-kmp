package org.mycarcompanion.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MechanicProfile(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("shop_name") val shopName: String? = null,
    @SerialName("shop_type") val shopType: String = "general",
    val bio: String? = null,
    val specialties: List<String>? = null,
    val certifications: List<String>? = null,
    @SerialName("years_experience") val yearsExperience: Int? = null,
    @SerialName("hourly_rate") val hourlyRate: Double? = null,
    val city: String? = null,
    val state: String? = null,
    @SerialName("profile_image_url") val profileImageUrl: String? = null,
    @SerialName("verification_status") val verificationStatus: String = "pending",
    @SerialName("verified_at") val verifiedAt: String? = null,
    @SerialName("is_available") val isAvailable: Boolean? = true,
    val rating: Double? = null,
    @SerialName("total_jobs") val totalJobs: Int? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
data class MechanicAssignment(
    val id: String = "",
    @SerialName("vehicle_id") val vehicleId: String = "",
    @SerialName("mechanic_user_id") val mechanicUserId: String = "",
    @SerialName("assigned_by") val assignedBy: String? = null,
    val status: String = "active",
    val notes: String? = null,
    @SerialName("assigned_at") val assignedAt: String = "",
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class MechanicProfileInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("shop_name") val shopName: String,
    @SerialName("shop_type") val shopType: String,
    val bio: String? = null,
    val city: String? = null,
    val state: String? = null,
    @SerialName("years_experience") val yearsExperience: Int? = null,
    @SerialName("hourly_rate") val hourlyRate: Double? = null,
    @SerialName("updated_at") val updatedAt: String,
)

val shopTypes = listOf(
    "general", "smog", "body_shop", "tire", "electrical", "detailing",
)

val shopTypeLabels = mapOf(
    "general" to "General",
    "smog" to "Smog",
    "body_shop" to "Body Shop",
    "tire" to "Tire",
    "electrical" to "Electrical",
    "detailing" to "Detailing",
)
