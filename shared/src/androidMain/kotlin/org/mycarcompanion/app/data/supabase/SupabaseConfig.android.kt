package org.mycarcompanion.app.data.supabase

actual object SupabaseConfig {
    private var _url: String = ""
    private var _anonKey: String = ""

    actual val url: String get() = _url
    actual val anonKey: String get() = _anonKey
    actual val authScheme: String = "org.mycarcompanion.app"
    actual val authHost: String = "auth"
    actual val authAutoSaveToStorage: Boolean = true

    fun init(url: String, anonKey: String) {
        _url = url
        _anonKey = anonKey
    }
}
