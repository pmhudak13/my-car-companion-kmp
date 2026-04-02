package org.mycarcompanion.app

import androidx.compose.runtime.Composable
import org.koin.compose.KoinContext
import org.mycarcompanion.app.ui.navigation.AppNavigation
import org.mycarcompanion.app.ui.theme.AppTheme

@Composable
fun App() {
    KoinContext {
        AppTheme {
            AppNavigation()
        }
    }
}
