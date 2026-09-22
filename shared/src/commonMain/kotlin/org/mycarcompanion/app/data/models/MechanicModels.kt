package org.mycarcompanion.app.data.models

import kotlinx.serialization.SerialName
import kotlin.math.round
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
    @SerialName("default_tax_rate") val defaultTaxRate: Double = 0.0,
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

@Serializable
data class MechanicJob(
    val id: String = "",
    @SerialName("mechanic_user_id") val mechanicUserId: String = "",
    @SerialName("vehicle_id") val vehicleId: String? = null,
    @SerialName("client_name") val clientName: String = "",
    @SerialName("client_email") val clientEmail: String? = null,
    @SerialName("vehicle_make") val vehicleMake: String = "",
    @SerialName("vehicle_model") val vehicleModel: String = "",
    @SerialName("vehicle_year") val vehicleYear: Int = 0,
    @SerialName("vehicle_vin") val vehicleVin: String? = null,
    @SerialName("vehicle_color") val vehicleColor: String? = null,
    @SerialName("vehicle_license_plate") val vehicleLicensePlate: String? = null,
    val description: String? = null,
    val status: String = "open",
    @SerialName("invite_sent") val inviteSent: Boolean = false,
    val notes: String? = null,
    @SerialName("progress_percent") val progressPercent: Int = 0,
    @SerialName("total_cost") val totalCost: Double? = null,
    @SerialName("payment_received") val paymentReceived: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("estimate_approved_at") val estimateApprovedAt: String? = null,
    @SerialName("estimate_approved_by") val estimateApprovedBy: String? = null,
    @SerialName("estimate_approved_total") val estimateApprovedTotal: Double? = null,
    @SerialName("estimate_approval_method") val estimateApprovalMethod: String? = null,
    @SerialName("tax_rate") val taxRate: Double = 0.0,
)

/** Mitchell-style document stage, derived from existing fields rather than stored. */
val MechanicJob.stage: String get() = when {
    paymentReceived -> "Paid"
    status == "completed" -> "Invoiced"
    estimateApprovedAt != null -> "Approved"
    else -> "Estimate"
}

/** Amount the customer has OK'd: the approved estimate plus any approved add-on issues. */
fun MechanicJob.authorizedTotal(issues: List<MechanicJobIssue>): Double =
    (estimateApprovedTotal ?: 0.0) + issues.filter { it.status == "approved" }.sumOf { it.estimatedCost ?: 0.0 }

@Serializable
data class JobLineItem(
    val id: String = "",
    @SerialName("mechanic_job_id") val mechanicJobId: String = "",
    @SerialName("mechanic_user_id") val mechanicUserId: String = "",
    val kind: String = "labor",
    val description: String = "",
    val quantity: Double = 1.0,
    @SerialName("unit_price") val unitPrice: Double = 0.0,
    @SerialName("created_at") val createdAt: String = "",
) {
    val lineTotal: Double get() = quantity * unitPrice
}

// These mirror the DB function mechanic_job_total(): only parts are taxed, so a fee or
// discount line never moves the tax. Keep the two in step if either changes.
private fun round2(v: Double): Double = round(v * 100) / 100

fun List<JobLineItem>.subtotal(): Double = sumOf { it.lineTotal }

fun List<JobLineItem>.taxAmount(rate: Double): Double =
    round2(filter { it.kind == "part" }.sumOf { it.lineTotal } * rate / 100)

/** Null when there are no line items, matching the DB (a typed total is kept until lines exist). */
fun List<JobLineItem>.jobTotal(rate: Double): Double? =
    if (isEmpty()) null else round2(subtotal()) + taxAmount(rate)

/** How the customer gave approval. The server forces "in_app" when the owner responds themselves. */
val approvalMethodLabels = linkedMapOf(
    "in_person" to "In person",
    "phone" to "By phone",
    "text" to "By text",
    "in_app" to "In app",
)

@Serializable
data class CannedLine(
    val kind: String,
    val description: String,
    val quantity: Double,
    @SerialName("unit_price") val unitPrice: Double,
)

/** Mitchell "Canned Job": a mechanic's saved set of line items, applied to a job in one tap. */
@Serializable
data class CannedJob(
    val id: String = "",
    @SerialName("mechanic_user_id") val mechanicUserId: String = "",
    val name: String = "",
    val lines: List<CannedLine> = emptyList(),
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class CannedJobInsert(
    @SerialName("mechanic_user_id") val mechanicUserId: String,
    val name: String,
    val lines: List<CannedLine>,
)

@Serializable
data class JobLineItemInsert(
    @SerialName("mechanic_job_id") val mechanicJobId: String,
    @SerialName("mechanic_user_id") val mechanicUserId: String,
    val kind: String,
    val description: String,
    val quantity: Double,
    @SerialName("unit_price") val unitPrice: Double,
)

@Serializable
data class MechanicJobIssue(
    val id: String = "",
    @SerialName("mechanic_job_id") val mechanicJobId: String = "",
    @SerialName("mechanic_user_id") val mechanicUserId: String = "",
    val title: String = "",
    val description: String? = null,
    @SerialName("estimated_cost") val estimatedCost: Double? = null,
    val status: String = "pending",
    @SerialName("owner_response") val ownerResponse: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("responded_at") val respondedAt: String? = null,
    @SerialName("approval_method") val approvalMethod: String? = null,
)

@Serializable
data class MechanicJobIssueInsert(
    @SerialName("mechanic_job_id") val mechanicJobId: String,
    @SerialName("mechanic_user_id") val mechanicUserId: String,
    val title: String,
    val description: String? = null,
    @SerialName("estimated_cost") val estimatedCost: Double? = null,
)

@Serializable
data class MechanicJobMedia(
    val id: String = "",
    @SerialName("mechanic_job_id") val mechanicJobId: String = "",
    @SerialName("mechanic_user_id") val mechanicUserId: String = "",
    @SerialName("storage_path") val storagePath: String = "",
    @SerialName("media_type") val mediaType: String = "image",
    @SerialName("file_name") val fileName: String = "",
    val caption: String? = null,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class MechanicJobMediaInsert(
    @SerialName("mechanic_job_id") val mechanicJobId: String,
    @SerialName("mechanic_user_id") val mechanicUserId: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("media_type") val mediaType: String = "image",
    @SerialName("file_name") val fileName: String,
    val caption: String? = null,
)

@Serializable
data class MechanicJobLog(
    val id: String = "",
    @SerialName("mechanic_job_id") val mechanicJobId: String = "",
    @SerialName("mechanic_user_id") val mechanicUserId: String = "",
    val category: String = "",
    val description: String = "",
    val date: String = "",
    val mileage: Int = 0,
    val cost: Double? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("edit_notes") val editNotes: String? = null,
)

@Serializable
data class MechanicJobInsert(
    @SerialName("mechanic_user_id") val mechanicUserId: String,
    @SerialName("client_name") val clientName: String,
    @SerialName("client_email") val clientEmail: String? = null,
    @SerialName("vehicle_make") val vehicleMake: String,
    @SerialName("vehicle_model") val vehicleModel: String,
    @SerialName("vehicle_year") val vehicleYear: Int,
    @SerialName("vehicle_vin") val vehicleVin: String? = null,
    @SerialName("vehicle_color") val vehicleColor: String? = null,
    @SerialName("vehicle_license_plate") val vehicleLicensePlate: String? = null,
    val description: String? = null,
    val notes: String? = null,
)

@Serializable
data class MechanicJobLogInsert(
    @SerialName("mechanic_job_id") val mechanicJobId: String,
    @SerialName("mechanic_user_id") val mechanicUserId: String,
    val category: String,
    val description: String,
    val date: String,
    val mileage: Int,
    val cost: Double? = null,
    val notes: String? = null,
)

@Serializable
data class JobRequest(
    val id: String = "",
    @SerialName("owner_id") val ownerId: String = "",
    @SerialName("vehicle_id") val vehicleId: String? = null,
    @SerialName("vehicle_label") val vehicleLabel: String = "",
    val title: String = "",
    val description: String? = null,
    val city: String? = null,
    val state: String? = null,
    val status: String = "open",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class JobRequestInsert(
    @SerialName("owner_id") val ownerId: String,
    @SerialName("vehicle_id") val vehicleId: String? = null,
    @SerialName("vehicle_label") val vehicleLabel: String,
    val title: String,
    val description: String? = null,
    val city: String? = null,
    val state: String? = null,
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
