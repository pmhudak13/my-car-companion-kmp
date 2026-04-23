package org.mycarcompanion.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceToken(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val token: String = "",
    val platform: String = "",
)

@Serializable
data class NotificationPreferences(
    @SerialName("user_id") val userId: String = "",
    @SerialName("oil_change") val oilChange: Boolean = true,
    @SerialName("tire_rotation") val tireRotation: Boolean = true,
    val registration: Boolean = true,
    @SerialName("custom_reminders") val customReminders: Boolean = true,
    @SerialName("new_messages") val newMessages: Boolean = true,
    @SerialName("mechanic_updates") val mechanicUpdates: Boolean = true,
)
