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

@Composable
fun AppNavigation() {
    val authRepository: AuthRepository = koinInject()
    val authState = authRepository.authState.collectAsState(initial = AuthState.Loading)

    // Keep a single, stable Navigator — let LoginScreen/HomeScreen handle
    // auth-driven transitions internally via LaunchedEffect.
    // Recreating Navigator on auth state changes causes simultaneous navigation
    // calls from both AppNavigation and the active Screen's LaunchedEffect, which
    // tears Voyager's state and crashes the app.
    when (authState.value) {
        is AuthState.Loading -> LoadingScreen()
        else -> Navigator(LoginScreen())
    }
}

@Composable
fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
