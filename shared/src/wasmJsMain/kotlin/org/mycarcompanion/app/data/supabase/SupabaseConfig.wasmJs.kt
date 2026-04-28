package org.mycarcompanion.app.data.supabase

private val supabaseUrl: JsString = js("window.__SUPABASE_URL__")
private val supabaseAnonKey: JsString = js("window.__SUPABASE_ANON_KEY__")

// Returns true only when localStorage is available (false in private browsing / strict security mode).
private val localStorageAvailable: Boolean =
    js("(function(){try{localStorage.setItem('_t','1');localStorage.removeItem('_t');return true;}catch(e){return false;}})()")

actual object SupabaseConfig {
    actual val url: String = supabaseUrl.toString()
    actual val anonKey: String = supabaseAnonKey.toString()
    actual val authScheme: String = ""
    actual val authHost: String = ""
    actual val authAutoSaveToStorage: Boolean = localStorageAvailable
    actual val checkoutSuccessUrl: String = "https://www.mycarcompanion.org/app/?checkout=success"
    actual val checkoutCancelUrl: String = "https://www.mycarcompanion.org/app/"
    actual val portalReturnUrl: String = "https://www.mycarcompanion.org/app/"
}
