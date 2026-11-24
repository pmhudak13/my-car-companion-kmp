package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.mycarcompanion.app.data.models.MechanicFollowUp
import org.mycarcompanion.app.data.models.MechanicJobNote
import org.mycarcompanion.app.data.models.MechanicJobNoteInsert

/** Job notes the customer acknowledges, and the mechanic's follow-ups on a job. */
class JobNotesRepository(private val client: SupabaseClient) {

    private val notes get() = client.postgrest["mechanic_job_notes"]
    private val followUps get() = client.postgrest["mechanic_follow_ups"]

    suspend fun getNotes(jobId: String): Result<List<MechanicJobNote>> = runCatching {
        notes.select {
            filter { eq("mechanic_job_id", jobId) }
            order("created_at", Order.ASCENDING)
        }.decodeList<MechanicJobNote>()
    }

    suspend fun addNote(jobId: String, body: String): Result<MechanicJobNote> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        notes.insert(MechanicJobNoteInsert(jobId, userId, body.trim())) { select() }.decodeSingle<MechanicJobNote>()
    }

    suspend fun deleteNote(noteId: String): Result<Unit> = runCatching {
        notes.delete { filter { eq("id", noteId) } }
    }

    /** The server records "in_app" when the owner calls this, whatever [method] says. */
    suspend fun acknowledgeNote(noteId: String, method: String = "in_app"): Result<Unit> = runCatching {
        client.postgrest.rpc("acknowledge_mechanic_job_note", buildJsonObject {
            put("p_note_id", noteId)
            put("p_method", method)
        })
    }

    suspend fun getFollowUps(jobId: String): Result<List<MechanicFollowUp>> = runCatching {
        followUps.select {
            filter { eq("mechanic_job_id", jobId) }
            order("due_date", Order.ASCENDING)
        }.decodeList<MechanicFollowUp>()
    }

    /** Follow-ups still open across all of this mechanic's jobs, soonest first. */
    suspend fun getMyOpenFollowUps(): Result<List<MechanicFollowUp>> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        followUps.select {
            filter {
                eq("mechanic_user_id", userId)
                exact("done_at", null)
            }
            order("due_date", Order.ASCENDING)
        }.decodeList<MechanicFollowUp>()
    }

    suspend fun addFollowUp(jobId: String, title: String, dueDate: String, discountPercent: Int?): Result<Unit> = runCatching {
        client.postgrest.rpc("create_mechanic_follow_up", buildJsonObject {
            put("p_job_id", jobId)
            put("p_title", title.trim())
            put("p_due_date", dueDate)
            discountPercent?.let { put("p_discount_percent", it) }
        })
    }

    /** Also the way to cancel one: it turns off the customer's reminder too (a plain delete wouldn't). */
    suspend fun completeFollowUp(id: String): Result<Unit> = runCatching {
        client.postgrest.rpc("complete_mechanic_follow_up", buildJsonObject { put("p_id", id) })
    }
}
