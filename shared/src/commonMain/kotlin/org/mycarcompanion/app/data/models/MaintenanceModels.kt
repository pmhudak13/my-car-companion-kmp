package org.mycarcompanion.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MaintenanceLog(
    val id: String = "",
    @SerialName("vehicle_id") val vehicleId: String = "",
    @SerialName("created_by_user_id") val createdByUserId: String? = null,
    @SerialName("mechanic_assignment_id") val mechanicAssignmentId: String? = null,
    val category: String = "",
    val description: String = "",
    val date: String = "",
    val mileage: Int = 0,
    val cost: Double? = null,
    val notes: String? = null,
    @SerialName("labor_hours") val laborHours: Double? = null,
    @SerialName("parts_used") val partsUsed: List<String>? = null,
    // photo_urls: list of Supabase Storage public URLs
    @SerialName("photo_urls") val photoUrls: List<String>? = null,
    // approval_status: null/"approved" = visible; "pending" = awaiting owner approval; "rejected" = hidden
    @SerialName("approval_status") val approvalStatus: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)
// DB migrations:
// ALTER TABLE maintenance_logs ADD COLUMN IF NOT EXISTS photo_urls TEXT[] DEFAULT '{}';
// ALTER TABLE maintenance_logs ADD COLUMN IF NOT EXISTS approval_status TEXT DEFAULT NULL;

data class MaintenanceFormData(
    val category: String = "",
    val description: String = "",
    val date: String = "",
    val mileage: String = "",
    val cost: String = "",
    val notes: String = "",
    val photoUris: List<ByteArray> = emptyList(), // in-memory before upload
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
