package org.mycarcompanion.app

import android.app.Application
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineExceptionHandler
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.mycarcompanion.androidapp.BuildConfig
import org.mycarcompanion.app.di.appModule

class MyApp : Application() {

    // Global handler for unhandled coroutine exceptions.
    // Sentry will also capture these automatically once initialized,
    // but this ensures nothing silently disappears in debug builds.
    val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        throwable.printStackTrace()
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.SENTRY_DSN.isNotEmpty()) {
            SentryAndroid.init(this) { options ->
                options.dsn = BuildConfig.SENTRY_DSN
                // Only send sessions data in release builds
                options.isEnableAutoSessionTracking = !BuildConfig.DEBUG
                // Attach screenshots to crash reports so you can see exactly
                // what was on screen when the crash happened
                options.isAttachScreenshot = true
                // Capture 100% of transactions in debug, 20% in production
                options.tracesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.2
            }
        }

        startKoin {
            androidContext(this@MyApp)
            modules(appModule)
        }
    }
}
