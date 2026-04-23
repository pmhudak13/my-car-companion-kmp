package org.mycarcompanion.app.ui.profile

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.repository.AuthRepository
import org.mycarcompanion.app.data.repository.ProfileRepository

data class ProfileUiState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val resetEmailSent: Boolean = false,
)

class ProfileScreenModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        screenModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val result = profileRepository.getMyProfile()
            val profile = result.getOrNull()
            _state.value = _state.value.copy(
                loading = false,
                firstName = profile?.firstName ?: "",
                lastName = profile?.lastName ?: "",
                email = profile?.email ?: "",
                error = if (result.isFailure) result.exceptionOrNull()?.message else null,
            )
        }
    }

    fun updateFirstName(value: String) {
        _state.value = _state.value.copy(firstName = value, saved = false)
    }

    fun updateLastName(value: String) {
        _state.value = _state.value.copy(lastName = value, saved = false)
    }

    fun save() {
        screenModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            val result = profileRepository.updateProfile(
                firstName = _state.value.firstName,
                lastName = _state.value.lastName,
            )
            if (result.isSuccess) {
                _state.value = _state.value.copy(saving = false, saved = true)
            } else {
                _state.value = _state.value.copy(
                    saving = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to save profile",
                )
            }
        }
    }

    fun sendPasswordReset() {
        val email = _state.value.email.ifEmpty { return }
        screenModelScope.launch {
            authRepository.sendPasswordReset(email)
                .onSuccess {
                    _state.value = _state.value.copy(resetEmailSent = true)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to send reset email")
                }
        }
    }

    fun clearSaved() {
        _state.value = _state.value.copy(saved = false)
    }

    fun clearResetSent() {
        _state.value = _state.value.copy(resetEmailSent = false)
    }
}
