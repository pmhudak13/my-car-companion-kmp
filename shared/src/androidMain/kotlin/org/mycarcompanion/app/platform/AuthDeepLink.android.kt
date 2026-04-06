package org.mycarcompanion.app.platform

import android.content.Intent
import io.github.jan.supabase.auth.handleDeeplinks
import org.mycarcompanion.app.data.supabase.supabaseClient

fun handleAuthDeepLinkIntent(intent: Intent) {
    supabaseClient.handleDeeplinks(intent)
}
