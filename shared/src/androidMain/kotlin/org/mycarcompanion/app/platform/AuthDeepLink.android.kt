package org.mycarcompanion.app.platform

import android.content.Intent
import io.github.jan.supabase.auth.handleDeeplinks
import org.mycarcompanion.app.data.supabase.supabaseClient

fun handleAuthDeepLinkIntent(intent: Intent) {
    try {
        supabaseClient.handleDeeplinks(intent)
    } catch (e: Exception) {
        // Don't crash on deep link errors; auth will remain in its current state
        println("AuthDeepLink: failed to handle deep link - ${e.message}")
    }
}
