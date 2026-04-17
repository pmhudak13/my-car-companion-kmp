package org.mycarcompanion.app.data.repository

// Required Supabase table:
// CREATE TABLE mechanic_reviews (
//   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
//   mechanic_user_id UUID NOT NULL REFERENCES auth.users(id),
//   reviewer_id UUID NOT NULL REFERENCES auth.users(id),
//   rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
//   comment TEXT,
//   created_at TIMESTAMPTZ DEFAULT NOW(),
//   UNIQUE(mechanic_user_id, reviewer_id)
// );
// ALTER TABLE mechanic_reviews ENABLE ROW LEVEL SECURITY;
// CREATE POLICY "Anyone can read reviews" ON mechanic_reviews FOR SELECT USING (true);
// CREATE POLICY "Users can insert their own reviews"
//   ON mechanic_reviews FOR INSERT WITH CHECK (reviewer_id = auth.uid());
// CREATE POLICY "Users can update their own reviews"
//   ON mechanic_reviews FOR UPDATE USING (reviewer_id = auth.uid());

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
                set("comment", trimmedComment)
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
