package org.mycarcompanion.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MileageTrip(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("vehicle_id") val vehicleId: String? = null,
    val purpose: String = "business",
    val notes: String? = null,
    @SerialName("start_lat") val startLat: Double? = null,
    @SerialName("start_lng") val startLng: Double? = null,
    @SerialName("end_lat") val endLat: Double? = null,
    @SerialName("end_lng") val endLng: Double? = null,
    @SerialName("distance_miles") val distanceMiles: Double = 0.0,
    @SerialName("started_at") val startedAt: String = "",
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("created_at") val createdAt: String = "",
)

data class MileageTripFormData(
    val vehicleId: String = "",
    val purpose: String = "business",
    val notes: String = "",
    val distanceMiles: String = "",
)

val tripPurposes = listOf("business", "personal", "medical", "charity")

val tripPurposeLabels = mapOf(
    "business" to "Business",
    "personal" to "Personal",
    "medical" to "Medical",
    "charity" to "Charity",
)
