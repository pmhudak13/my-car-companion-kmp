package org.mycarcompanion.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceToken(
    @kotlinx.serialization.Transient val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val token: String = "",
    val platform: String = "",
)

@Serializable
data class NotificationPreferences(
    @SerialName("user_id") val userId: String = "",
    // Push preferences
    @SerialName("oil_change") val oilChange: Boolean = true,
    @SerialName("tire_rotation") val tireRotation: Boolean = true,
    val registration: Boolean = true,
    @SerialName("custom_reminders") val customReminders: Boolean = true,
    @SerialName("new_messages") val newMessages: Boolean = true,
    @SerialName("mechanic_updates") val mechanicUpdates: Boolean = true,
    // Email preferences
    @SerialName("email_oil_change") val emailOilChange: Boolean = true,
    @SerialName("email_tire_rotation") val emailTireRotation: Boolean = true,
    @SerialName("email_registration") val emailRegistration: Boolean = true,
    @SerialName("email_custom_reminders") val emailCustomReminders: Boolean = true,
    @SerialName("email_new_messages") val emailNewMessages: Boolean = true,
    @SerialName("email_mechanic_updates") val emailMechanicUpdates: Boolean = true,
)
