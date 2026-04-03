package org.mycarcompanion.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.mycarcompanion.androidapp.BuildConfig
import org.mycarcompanion.app.data.supabase.SupabaseConfig
import org.mycarcompanion.app.di.appModule
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
        startKoin {
            androidContext(applicationContext)
            modules(appModule)
        }
        setContent {
            App()
        }
    }
}
