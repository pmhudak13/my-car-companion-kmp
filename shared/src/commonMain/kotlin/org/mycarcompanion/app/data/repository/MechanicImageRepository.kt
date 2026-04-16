package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class MechanicJobImage(
    val id: String = "",
    @SerialName("mechanic_job_id") val mechanicJobId: String = "",
    @SerialName("mechanic_user_id") val mechanicUserId: String = "",
    @SerialName("storage_path") val storagePath: String = "",
    val caption: String? = null,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class MechanicJobImageInsert(
    @SerialName("mechanic_job_id") val mechanicJobId: String,
    @SerialName("mechanic_user_id") val mechanicUserId: String,
    @SerialName("storage_path") val storagePath: String,
    val caption: String? = null,
)

class MechanicImageRepository(private val client: SupabaseClient) {

    private val bucket get() = client.storage["mechanic-job-images"]
    private val table get() = client.postgrest["mechanic_job_images"]

    @OptIn(ExperimentalUuidApi::class)
    suspend fun uploadImage(
        jobId: String,
        imageBytes: ByteArray,
        caption: String? = null,
    ): Result<MechanicJobImage> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        val fileName = "${Uuid.random()}.jpg"
        val storagePath = "$userId/$jobId/$fileName"

        bucket.upload(storagePath, imageBytes) {
            upsert = false
        }

        table.insert(
            MechanicJobImageInsert(
                mechanicJobId = jobId,
                mechanicUserId = userId,
                storagePath = storagePath,
                caption = caption,
            ),
        ) { select() }.decodeSingle<MechanicJobImage>()
    }

    suspend fun getImagesForJob(jobId: String): Result<List<MechanicJobImage>> = runCatching {
        table.select {
            filter { eq("mechanic_job_id", jobId) }
            order("created_at", Order.ASCENDING)
        }.decodeList<MechanicJobImage>()
    }

    suspend fun getSignedUrl(storagePath: String): Result<String> = runCatching {
        bucket.createSignedUrl(storagePath, expiresIn = 3600)
    }

    suspend fun deleteImage(imageId: String, storagePath: String): Result<Unit> = runCatching {
        bucket.delete(storagePath)
        table.delete { filter { eq("id", imageId) } }
    }
}
