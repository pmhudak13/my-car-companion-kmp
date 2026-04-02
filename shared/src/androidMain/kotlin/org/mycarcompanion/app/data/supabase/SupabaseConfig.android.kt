package org.mycarcompanion.app.data.supabase

import org.mycarcompanion.androidapp.BuildConfig

actual object SupabaseConfig {
    actual val url: String = BuildConfig.SUPABASE_URL
    actual val anonKey: String = BuildConfig.SUPABASE_ANON_KEY
}
