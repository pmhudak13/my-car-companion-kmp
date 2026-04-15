package org.mycarcompanion.app.data.repository

// Required Supabase Storage setup:
// 1. Create bucket "avatars" (public) in Supabase Dashboard → Storage
// 2. Create bucket "maintenance-photos" (public) in Supabase Dashboard → Storage
// 3. Add RLS policies for each bucket:
//    -- avatars: allow auth users to upload their own files
//    CREATE POLICY "Users upload own avatar" ON storage.objects
//      FOR INSERT WITH CHECK (bucket_id = 'avatars' AND auth.uid()::text = (storage.foldername(name))[1]);
//    CREATE POLICY "Anyone reads avatars" ON storage.objects
//      FOR SELECT USING (bucket_id = 'avatars');
//    -- maintenance-photos: allow auth users to upload
//    CREATE POLICY "Auth users upload maintenance photos" ON storage.objects
//      FOR INSERT WITH CHECK (bucket_id = 'maintenance-photos' AND auth.role() = 'authenticated');
//    CREATE POLICY "Anyone reads maintenance photos" ON storage.objects
//      FOR SELECT USING (bucket_id = 'maintenance-photos');

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage

class StorageRepository(private val client: SupabaseClient) {

    /**
     * Uploads avatar bytes for the current user and returns the public URL.
     * The file is stored as avatars/{userId}.jpg and upserted on every upload.
     */
    suspend fun uploadAvatar(bytes: ByteArray): Result<String> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        val path = "$userId/avatar.jpg"
        val bucket = client.storage["avatars"]
        bucket.upload(path, bytes) { upsert = true }
        bucket.publicUrl(path)
    }

    /**
     * Uploads a mechanic profile photo and returns the public URL.
     */
    suspend fun uploadMechanicPhoto(bytes: ByteArray): Result<String> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        val path = "$userId/profile.jpg"
        val bucket = client.storage["avatars"]
        bucket.upload(path, bytes) { upsert = true }
        bucket.publicUrl(path)
    }

    /**
     * Uploads a maintenance photo and returns the public URL.
     * [logId] is the maintenance log id, [index] differentiates multiple photos.
     */
    suspend fun uploadMaintenancePhoto(logId: String, index: Int, bytes: ByteArray): Result<String> = runCatching {
        val path = "$logId/$index.jpg"
        val bucket = client.storage["maintenance-photos"]
        bucket.upload(path, bytes) { upsert = true }
        bucket.publicUrl(path)
    }
}
