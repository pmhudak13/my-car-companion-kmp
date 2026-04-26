package org.mycarcompanion.app.data.supabase

expect object SupabaseConfig {
    val url: String
    val anonKey: String
    /** Deep-link URI scheme used by Supabase Auth on Android. Empty on web (no deep links). */
    val authScheme: String
    /** Deep-link URI host used by Supabase Auth on Android. Empty on web (no deep links). */
    val authHost: String
    /** Whether Supabase Auth may persist sessions to storage. False when storage is unavailable (e.g. private browsing). */
    val authAutoSaveToStorage: Boolean
}
