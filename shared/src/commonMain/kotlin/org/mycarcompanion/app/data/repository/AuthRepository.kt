package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mycarcompanion.app.data.models.AppUser
import org.mycarcompanion.app.data.models.AuthResult
import org.mycarcompanion.app.data.models.AuthState
import org.mycarcompanion.app.platform.googleAuthRedirectUrl

class AuthRepository(private val client: SupabaseClient) {

    val authState: Flow<AuthState> = client.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> {
                val user = client.auth.currentUserOrNull()
                if (user != null) {
                    AuthState.Authenticated(
                        AppUser(
                            id = user.id,
                            email = user.email ?: "",
                        )
                    )
                } else {
                    AuthState.Unauthenticated
                }
            }
            is SessionStatus.NotAuthenticated -> AuthState.Unauthenticated
            is SessionStatus.Initializing -> AuthState.Loading
            is SessionStatus.RefreshFailure -> AuthState.Unauthenticated
        }
    }

    suspend fun signIn(email: String, password: String): AuthResult = try {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        AuthResult.Success
    } catch (e: Exception) {
        AuthResult.Error(e.message ?: "Sign in failed")
    }

    suspend fun signUp(email: String, password: String): AuthResult = try {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        AuthResult.Success
    } catch (e: Exception) {
        AuthResult.Error(e.message ?: "Sign up failed")
    }

    suspend fun signInWithGoogle(): AuthResult = try {
        client.auth.signInWith(Google, redirectUrl = googleAuthRedirectUrl)
        AuthResult.Success
    } catch (e: Exception) {
        AuthResult.Error(e.message ?: "Google sign-in failed")
    }

    suspend fun signOut(): AuthResult = try {
        client.auth.signOut()
        AuthResult.Success
    } catch (e: Exception) {
        AuthResult.Error(e.message ?: "Sign out failed")
    }
}
