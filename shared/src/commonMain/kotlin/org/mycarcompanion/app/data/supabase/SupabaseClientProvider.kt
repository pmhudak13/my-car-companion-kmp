package org.mycarcompanion.app.data.supabase

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime

val supabaseClient by lazy {
    createSupabaseClient(
        supabaseUrl = SupabaseConfig.url,
        supabaseKey = SupabaseConfig.anonKey
    ) {
        install(Auth) {
            autoSaveToStorage = SupabaseConfig.authAutoSaveToStorage
            scheme = SupabaseConfig.authScheme
            host = SupabaseConfig.authHost
        }
        install(Postgrest)
        install(Storage)
        install(Realtime)
        install(Functions)
    }
}

fun prewarmSupabaseClient() {
    supabaseClient
}

suspend fun importSessionTokens(accessToken: String, refreshToken: String) {
    supabaseClient.auth.importAuthToken(
        accessToken = accessToken,
        refreshToken = refreshToken,
        retrieveUser = true,
    )
}

