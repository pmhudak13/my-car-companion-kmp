package org.mycarcompanion.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VehicleTransfer(
    val id: String = "",
    @SerialName("vehicle_id") val vehicleId: String = "",
    @SerialName("sender_id") val fromUserId: String = "",
    @SerialName("transfer_code") val transferCode: String = "",
    @SerialName("claimed_by") val claimedById: String? = null,
    @SerialName("claimed_at") val claimedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class VehicleTransferInsert(
    @SerialName("vehicle_id") val vehicleId: String,
    @SerialName("sender_id") val fromUserId: String,
    @SerialName("transfer_code") val transferCode: String,
    @SerialName("expires_at") val expiresAt: String,
)
