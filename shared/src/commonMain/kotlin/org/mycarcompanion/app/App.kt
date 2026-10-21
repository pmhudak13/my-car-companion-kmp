package org.mycarcompanion.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.koin.compose.KoinContext
import org.mycarcompanion.app.ui.navigation.AppNavigation
import org.mycarcompanion.app.ui.theme.AppTheme

@Composable
fun App() {
    KoinContext {
        AppTheme {
            // Paint the theme background so screens without a Scaffold (login, sign-up) don't
            // show dark-mode text on the always-light Android window background.
            Surface(modifier = Modifier.fillMaxSize()) {
                AppNavigation()
            }
        }
    }
}
