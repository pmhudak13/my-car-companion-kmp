package org.mycarcompanion.app.data.supabase

actual object SupabaseConfig {
    actual val url: String = "https://eoqycogokelfmgwqydkj.supabase.co"
    actual val anonKey: String = "" // TODO: inject via Info.plist
    actual val authScheme: String = "org.mycarcompanion.app"
    actual val authHost: String = "auth"
    actual val authAutoSaveToStorage: Boolean = true
    actual val checkoutSuccessUrl: String = "org.mycarcompanion.app://subscription/success"
    actual val checkoutCancelUrl: String = "org.mycarcompanion.app://subscription/cancel"
    actual val portalReturnUrl: String = "org.mycarcompanion.app://subscription/portal-return"
}
