package org.mycarcompanion.app.ui.navigation

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import org.mycarcompanion.app.ui.auth.LoginScreen

// The Navigator is created ONCE and never destroyed based on auth state.
//
// Why: Supabase auth emits SessionStatus.Initializing (→ AuthState.Loading)
// during sign-in and session refresh. If AppNavigation reacts to Loading by
// swapping in a LoadingScreen, the Navigator composable leaves the tree.
// Every active screen holds a LocalNavigator reference that instantly becomes
// invalid, so the very next navigator.push/replace call crashes with NPE.
//
// Auth-driven screen transitions (Login → Home, Home → Login on sign-out)
// are handled inside each Screen via LaunchedEffect. AppNavigation's only
// job is to mount the root Navigator once.
@Composable
fun AppNavigation() {
    Navigator(LoginScreen())
}
