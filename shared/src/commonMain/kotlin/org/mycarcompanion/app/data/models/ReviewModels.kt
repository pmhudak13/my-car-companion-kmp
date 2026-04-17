package org.mycarcompanion.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MechanicReview(
    val id: String = "",
    @SerialName("mechanic_user_id") val mechanicUserId: String = "",
    @SerialName("reviewer_user_id") val reviewerId: String = "",
    val rating: Int = 0,
    val comment: String? = null,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class MechanicReviewInsert(
    @SerialName("mechanic_user_id") val mechanicUserId: String,
    @SerialName("reviewer_user_id") val reviewerId: String,
    val rating: Int,
    val comment: String? = null,
)
