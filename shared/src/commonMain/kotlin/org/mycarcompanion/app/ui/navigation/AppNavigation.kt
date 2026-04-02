package org.mycarcompanion.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator
import org.koin.compose.koinInject
import org.mycarcompanion.app.data.models.AuthState
import org.mycarcompanion.app.data.repository.AuthRepository
import org.mycarcompanion.app.ui.auth.LoginScreen
import org.mycarcompanion.app.ui.home.HomeScreen

@Composable
fun AppNavigation() {
    val authRepository: AuthRepository = koinInject()
    val authState = authRepository.authState.collectAsState(initial = AuthState.Loading)

    when (val state = authState.value) {
        is AuthState.Loading -> LoadingScreen()
        is AuthState.Unauthenticated -> Navigator(LoginScreen())
        is AuthState.Authenticated -> Navigator(HomeScreen(state.user))
    }
}

@Composable
fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
