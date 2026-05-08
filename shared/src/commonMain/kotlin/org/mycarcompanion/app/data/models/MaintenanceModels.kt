package org.mycarcompanion.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MaintenanceLog(
    val id: String = "",
    @SerialName("vehicle_id") val vehicleId: String = "",
    @SerialName("created_by_user_id") val createdByUserId: String? = null,
    @SerialName("mechanic_assignment_id") val mechanicAssignmentId: String? = null,
    @SerialName("mechanic_job_log_id") val mechanicJobLogId: String? = null,
    val source: String = "self",
    val category: String = "",
    val description: String = "",
    val date: String = "",
    val mileage: Int = 0,
    val cost: Double? = null,
    val notes: String? = null,
    @SerialName("edit_notes") val editNotes: String? = null,
    @SerialName("labor_hours") val laborHours: Double? = null,
    @SerialName("parts_used") val partsUsed: List<String>? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

data class MaintenanceFormData(
    val category: String = "",
    val description: String = "",
    val date: String = "",
    val mileage: String = "",
    val cost: String = "",
    val notes: String = "",
)

val maintenanceCategories = listOf(
    "Oil Change",
    "Tire Rotation",
    "Brake Service",
    "Air Filter",
    "Cabin Filter",
    "Transmission Service",
    "Coolant Flush",
    "Battery Replacement",
    "Wiper Blades",
    "Spark Plugs",
    "Alignment",
    "Suspension",
    "Registration",
    "Inspection",
    "Smog Check",
    "Body Work",
    "Other",
)
