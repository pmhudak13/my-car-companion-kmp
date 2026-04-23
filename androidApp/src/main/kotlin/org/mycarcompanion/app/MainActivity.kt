package org.mycarcompanion.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.mycarcompanion.androidapp.BuildConfig
import org.mycarcompanion.app.data.models.AuthState
import org.mycarcompanion.app.data.repository.AuthRepository
import org.mycarcompanion.app.data.repository.DeviceTokenRepository
import org.mycarcompanion.app.data.supabase.SupabaseConfig
import org.mycarcompanion.app.platform.handleAuthDeepLinkIntent
import org.mycarcompanion.app.platform.initGeolocation

class MainActivity : ComponentActivity() {

    private val deviceTokenRepository: DeviceTokenRepository by inject()
    private val authRepository: AuthRepository by inject()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled silently — user can re-enable in Settings */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SupabaseConfig.init(
            url = BuildConfig.SUPABASE_URL,
            anonKey = BuildConfig.SUPABASE_ANON_KEY,
        )
        initGeolocation(applicationContext)
        requestNotificationPermissionIfNeeded()
        registerFcmTokenWhenAuthenticated()
        handleAuthIntent(intent)
        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent) {
        if (intent.data?.scheme == "org.mycarcompanion.app") {
            handleAuthDeepLinkIntent(intent)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Waits for the user to be authenticated before uploading the FCM token so that
    // first-install users (no session yet) still get their token registered after login.
    private fun registerFcmTokenWhenAuthenticated() {
        lifecycleScope.launch {
            authRepository.authState.filterIsInstance<AuthState.Authenticated>().first()
            val prefs = getSharedPreferences(MyFirebaseMessagingService.PREFS_NAME, MODE_PRIVATE)
            val storedToken = prefs.getString(MyFirebaseMessagingService.KEY_TOKEN, null)
            if (storedToken != null) {
                deviceTokenRepository.upsertToken(storedToken, "android")
            } else {
                // Belt-and-suspenders: OEM devices (e.g. OnePlus OxygenOS) can skip
                // FirebaseInitProvider, leaving Firebase uninitialised even after MyApp.onCreate().
                if (FirebaseApp.getApps(applicationContext).isEmpty()) {
                    FirebaseApp.initializeApp(applicationContext)
                }
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    lifecycleScope.launch { deviceTokenRepository.upsertToken(token, "android") }
                }
            }
        }
    }
}
