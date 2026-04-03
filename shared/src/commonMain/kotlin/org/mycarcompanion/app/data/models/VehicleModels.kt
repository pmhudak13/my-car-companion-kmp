package org.mycarcompanion.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Vehicle(
    val id: String = "",
    @SerialName("owner_id") val ownerId: String = "",
    val make: String = "",
    val model: String = "",
    val year: Int = 0,
    val color: String? = null,
    @SerialName("license_plate") val licensePlate: String? = null,
    val vin: String? = null,
    val odometer: Int = 0,
    val unit: String = "miles",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

data class VehicleFormData(
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val color: String = "",
    val licensePlate: String = "",
    val vin: String = "",
    val odometer: String = "0",
    val unit: String = "miles",
)
