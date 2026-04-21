package org.mycarcompanion.app.data.repository

// Actual mechanic_reviews schema:
//   id, mechanic_user_id, reviewer_user_id, assignment_id, rating, review_text, created_at, updated_at

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import org.mycarcompanion.app.data.models.MechanicReview
import org.mycarcompanion.app.data.models.MechanicReviewInsert

class ReviewRepository(private val client: SupabaseClient) {

    private val table get() = client.postgrest["mechanic_reviews"]

    suspend fun getReviewsForMechanic(mechanicUserId: String): Result<List<MechanicReview>> = runCatching {
        table.select {
            filter { eq("mechanic_user_id", mechanicUserId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<MechanicReview>()
    }

    suspend fun getMyReviewForMechanic(mechanicUserId: String): Result<MechanicReview?> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        table.select {
            filter {
                eq("mechanic_user_id", mechanicUserId)
                eq("reviewer_user_id", userId)
            }
        }.decodeList<MechanicReview>().firstOrNull()
    }

    suspend fun submitReview(mechanicUserId: String, rating: Int, comment: String?): Result<Unit> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        val trimmedComment = comment?.trim()?.takeIf { it.isNotBlank() }
        val existing = getMyReviewForMechanic(mechanicUserId).getOrNull()
        if (existing != null) {
            table.update({
                set("rating", rating)
                set("review_text", trimmedComment)
            }) {
                filter {
                    eq("mechanic_user_id", mechanicUserId)
                    eq("reviewer_user_id", userId)
                }
            }
        } else {
            table.insert(
                MechanicReviewInsert(
                    mechanicUserId = mechanicUserId,
                    reviewerId = userId,
                    rating = rating,
                    comment = trimmedComment,
                )
            )
        }
        Unit
    }
}
