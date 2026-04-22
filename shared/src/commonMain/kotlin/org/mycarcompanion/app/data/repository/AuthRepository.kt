package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.mycarcompanion.app.data.models.AppUser
import org.mycarcompanion.app.data.models.AuthResult
import org.mycarcompanion.app.data.models.AuthState
import org.mycarcompanion.app.platform.googleAuthRedirectUrl

class AuthRepository(
    private val client: SupabaseClient,
    private val profileRepository: ProfileRepository,
    private val appScope: CoroutineScope,
) {

    val authState: Flow<AuthState> = client.auth.sessionStatus.mapLatest { status ->
        when (status) {
            is SessionStatus.Authenticated -> {
                val user = client.auth.currentUserOrNull()
                if (user != null) {
                    try {
                        coroutineScope {
                            val isAdminDeferred = async { profileRepository.hasRole("admin").getOrDefault(false) }
                            val isMechanicDeferred = async { profileRepository.hasRole("mechanic").getOrDefault(false) }
                            val profileDeferred = async { profileRepository.getMyProfile().getOrNull() }
                            val isAdmin = isAdminDeferred.await()
                            val isMechanic = isMechanicDeferred.await()
                            val profile = profileDeferred.await()
                            val hasGoogleLinked = user.identities?.any { it.provider == "google" } ?: false
                            val intendedRole = user.userMetadata?.get("role")?.jsonPrimitive?.contentOrNull
                            AuthState.Authenticated(
                                AppUser(
                                    id = user.id,
                                    email = user.email ?: "",
                                    isAdmin = isAdmin,
                                    isMechanic = isMechanic,
                                    isPremium = profile?.isPremium ?: false,
                                    hasGoogleLinked = hasGoogleLinked,
                                    intendedRole = intendedRole,
                                )
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AuthState.Unauthenticated
                    }
                } else {
                    AuthState.Unauthenticated
                }
            }
            is SessionStatus.NotAuthenticated -> AuthState.Unauthenticated
            is SessionStatus.Initializing -> AuthState.Loading
            is SessionStatus.RefreshFailure -> AuthState.Unauthenticated
        }
    }.flowOn(Dispatchers.Default)
        .shareIn(appScope, SharingStarted.Eagerly, replay = 1)

    suspend fun signIn(email: String, password: String): AuthResult = try {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        AuthResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AuthResult.Error(e.message ?: "Sign in failed")
    }

    suspend fun signUp(email: String, password: String, role: String = "individual"): AuthResult = try {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            this.data = buildJsonObject { put("role", role) }
        }
        AuthResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AuthResult.Error(e.message ?: "Sign up failed")
    }

    suspend fun signInWithGoogle(): AuthResult = try {
        client.auth.signInWith(Google, redirectUrl = googleAuthRedirectUrl)
        AuthResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AuthResult.Error(e.message ?: "Google sign-in failed")
    }

    suspend fun linkGoogleIdentity(): AuthResult = try {
        client.auth.linkIdentity(Google, redirectUrl = googleAuthRedirectUrl)
        AuthResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AuthResult.Error(e.message ?: "Failed to link Google account")
    }

    suspend fun signOut(): AuthResult = try {
        client.auth.signOut()
        AuthResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AuthResult.Error(e.message ?: "Sign out failed")
    }

    fun getCurrentUserId(): String? = client.auth.currentUserOrNull()?.id
}
