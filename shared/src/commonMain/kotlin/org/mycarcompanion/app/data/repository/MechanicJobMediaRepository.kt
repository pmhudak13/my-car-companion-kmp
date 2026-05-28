package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.mycarcompanion.app.data.models.MechanicJobMedia
import org.mycarcompanion.app.data.models.MechanicJobMediaInsert
import org.mycarcompanion.app.data.supabase.SupabaseConfig

class MechanicJobMediaRepository(private val client: SupabaseClient) {

    private val table get() = client.postgrest["mechanic_job_media"]
    private val bucket get() = client.storage["mechanic-job-media"]

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun uploadMedia(
        jobId: String,
        fileName: String,
        base64Data: String,
        mimeType: String,
        caption: String?,
    ): Result<MechanicJobMedia> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        val bytes = Base64.decode(base64Data)
        val storagePath = "$userId/$jobId/$fileName"
        bucket.upload(storagePath, bytes) { upsert = false }
        val insert = MechanicJobMediaInsert(
            mechanicJobId = jobId,
            mechanicUserId = userId,
            storagePath = storagePath,
            mediaType = if (mimeType.startsWith("video")) "video" else "image",
            fileName = fileName,
            caption = caption?.ifBlank { null },
        )
        table.insert(insert) { select() }.decodeSingle<MechanicJobMedia>()
    }

    suspend fun getMediaForJob(jobId: String): Result<List<MechanicJobMedia>> = runCatching {
        table.select {
            filter { eq("mechanic_job_id", jobId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<MechanicJobMedia>()
    }

    suspend fun getMediaForJobs(jobIds: List<String>): Result<List<MechanicJobMedia>> = runCatching {
        if (jobIds.isEmpty()) return@runCatching emptyList()
        table.select {
            filter { isIn("mechanic_job_id", jobIds) }
            order("created_at", Order.DESCENDING)
        }.decodeList<MechanicJobMedia>()
    }

    suspend fun deleteMedia(media: MechanicJobMedia): Result<Unit> = runCatching {
        bucket.delete(media.storagePath)
        table.delete { filter { eq("id", media.id) } }
    }

    fun publicUrl(storagePath: String): String =
        "${SupabaseConfig.url}/storage/v1/object/public/mechanic-job-media/$storagePath"
}
