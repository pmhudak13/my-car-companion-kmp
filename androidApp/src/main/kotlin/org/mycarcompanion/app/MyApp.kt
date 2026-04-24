package org.mycarcompanion.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.google.firebase.FirebaseApp
import io.sentry.android.core.SentryAndroid
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.mycarcompanion.androidapp.BuildConfig
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
            }
        }

        startKoin {
            androidContext(this@MyApp)
            modules(appModule)
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
