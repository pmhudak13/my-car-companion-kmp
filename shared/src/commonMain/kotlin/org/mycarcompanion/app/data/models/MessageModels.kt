package org.mycarcompanion.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: String = "",
    @SerialName("sender_id") val senderId: String = "",
    @SerialName("recipient_id") val recipientId: String = "",
    @SerialName("vehicle_id") val vehicleId: String? = null,
    val content: String = "",
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class MessageInsert(
    @SerialName("sender_id") val senderId: String,
    @SerialName("recipient_id") val recipientId: String,
    val content: String,
    @SerialName("vehicle_id") val vehicleId: String? = null,
    @SerialName("image_path") val imagePath: String? = null,
)
