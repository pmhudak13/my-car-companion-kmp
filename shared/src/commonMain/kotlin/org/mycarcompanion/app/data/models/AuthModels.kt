package org.mycarcompanion.app.data.models

data class AppUser(
    val id: String,
    val email: String,
    val name: String = "",
    val isAdmin: Boolean = false,
    val isMechanic: Boolean = false,
    val isPremium: Boolean = false,
    val hasGoogleLinked: Boolean = false,
)

sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val user: AppUser) : AuthState()
}

sealed class AuthResult {
    object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
}
