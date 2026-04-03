package org.mycarcompanion.app.data.supabase

private val supabaseUrl: JsString = js("window.__SUPABASE_URL__")
private val supabaseAnonKey: JsString = js("window.__SUPABASE_ANON_KEY__")

actual object SupabaseConfig {
    actual val url: String = supabaseUrl.toString()
    actual val anonKey: String = supabaseAnonKey.toString()
}
