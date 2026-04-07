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
        setContent {
            App()
        }
        handleAuthIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent) {
        if (intent.data?.scheme == "org.mycarcompanion.app") {
            handleAuthDeepLinkIntent(intent)
        }
    }
}
