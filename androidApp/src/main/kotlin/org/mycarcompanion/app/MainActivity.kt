package org.mycarcompanion.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.mycarcompanion.androidapp.BuildConfig
import org.mycarcompanion.app.data.supabase.SupabaseConfig
import org.mycarcompanion.app.platform.handleAuthDeepLinkIntent
import org.mycarcompanion.app.platform.initGeolocation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SupabaseConfig.init(
            url = BuildConfig.SUPABASE_URL,
            anonKey = BuildConfig.SUPABASE_ANON_KEY,
        )
        initGeolocation(applicationContext)
        // Process deep-link before setContent so the first composition sees the correct auth state
        handleAuthIntent(intent)
        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keep getIntent() up-to-date for any later callers (e.g. after rotation)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent) {
        if (intent.data?.scheme == "org.mycarcompanion.app") {
            handleAuthDeepLinkIntent(intent)
        }
    }
}
