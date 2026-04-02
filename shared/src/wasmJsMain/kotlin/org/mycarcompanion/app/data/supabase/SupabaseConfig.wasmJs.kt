package org.mycarcompanion.app.data.supabase

actual object SupabaseConfig {
    // In production these come from build-time injection
    // For now read from JS global set by index.html
    actual val url: String = js("window.__SUPABASE_URL__").toString()
    actual val anonKey: String = js("window.__SUPABASE_ANON_KEY__").toString()
}
