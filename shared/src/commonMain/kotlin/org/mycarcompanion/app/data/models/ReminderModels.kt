package org.mycarcompanion.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Reminder(
    val id: String = "",
    @SerialName("vehicle_id") val vehicleId: String = "",
    val type: String = "",
    @SerialName("custom_name") val customName: String? = null,
    @SerialName("next_due_date") val nextDueDate: String? = null,
    @SerialName("next_due_mileage") val nextDueMileage: Int? = null,
    @SerialName("interval_months") val intervalMonths: Int? = null,
    @SerialName("interval_miles") val intervalMiles: Int? = null,
    @SerialName("is_system") val isSystem: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

data class ReminderFormData(
    val type: String = "",
    val customName: String = "",
    val nextDueDate: String = "",
    val nextDueMileage: String = "",
    val intervalMonths: String = "",
    val intervalMiles: String = "",
)

val reminderTypes = listOf(
    "oil_change",
    "tire_rotation",
    "registration",
    "custom",
)

val reminderTypeLabels = mapOf(
    "oil_change" to "Oil Change",
    "tire_rotation" to "Tire Rotation",
    "registration" to "Registration",
    "custom" to "Custom",
)
