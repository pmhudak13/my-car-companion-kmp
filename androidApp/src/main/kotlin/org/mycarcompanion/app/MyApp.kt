package org.mycarcompanion.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.google.firebase.FirebaseApp
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.mycarcompanion.androidapp.BuildConfig
import org.mycarcompanion.app.data.supabase.SupabaseConfig
import org.mycarcompanion.app.data.supabase.prewarmSupabaseClient
import org.mycarcompanion.app.di.appModule

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // FirebaseInitProvider is disabled in the manifest (OEM devices like OnePlus
        // OxygenOS can leave it in a broken partial state). We are the sole init point.
        FirebaseApp.initializeApp(this)

        if (BuildConfig.SENTRY_DSN.isNotEmpty()) {
            SentryAndroid.init(this) { options ->
                options.dsn = BuildConfig.SENTRY_DSN
                options.isEnableAutoSessionTracking = !BuildConfig.DEBUG
                options.isAttachScreenshot = true
                options.tracesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.2
                // Drop ANRs triggered by PairIP's license-check activity on sideloaded installs.
                // These are not our bugs — PairIP's LicenseActivity ANRs on non-Play installs.
                options.beforeSend = io.sentry.SentryOptions.BeforeSendCallback { event, _ ->
                    val viewNames = event.contexts?.app?.viewNames ?: return@BeforeSendCallback event
                    if (viewNames.any { it.contains("com.pairip.licensecheck") }) null else event
                }
            }
        }

        // Init Supabase config here so the client can be pre-warmed before Compose renders.
        SupabaseConfig.init(
            url = BuildConfig.SUPABASE_URL,
            anonKey = BuildConfig.SUPABASE_ANON_KEY,
        )

        startKoin {
            androidContext(this@MyApp)
            modules(appModule)
        }

        // Pre-warm supabaseClient on a background thread. Ktor's HttpClient static
        // initializers (ServiceLoader, reflection) are slow and would ANR the main thread
        // if triggered lazily during the first Compose frame (ANDROID-8).
        CoroutineScope(Dispatchers.IO).launch {
            prewarmSupabaseClient()
        }

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            MyFirebaseMessagingService.CHANNEL_ID,
            "My Car Companion",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Vehicle reminders and mechanic updates"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
