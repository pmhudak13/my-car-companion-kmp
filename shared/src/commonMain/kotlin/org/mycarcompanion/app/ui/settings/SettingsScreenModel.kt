package org.mycarcompanion.app.ui.settings

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import org.mycarcompanion.app.data.models.AuthResult
import org.mycarcompanion.app.data.repository.AuthRepository
import org.mycarcompanion.app.data.repository.DeviceTokenRepository
import org.mycarcompanion.app.data.repository.ProfileRepository

data class SettingsUiState(
    val signingOut: Boolean = false,
    val signedOut: Boolean = false,
    val isPremium: Boolean = false,
    val deletingAccount: Boolean = false,
    val deleteError: String? = null,
)

class SettingsScreenModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        screenModelScope.launch {
            profileRepository.getMyProfile().onSuccess { profile ->
                _state.update { it.copy(isPremium = profile?.isPremium == true) }
            }
        }
    }

    fun signOut() {
        screenModelScope.launch {
            _state.value = _state.value.copy(signingOut = true)
            deviceTokenRepository.deleteToken("android")
            authRepository.signOut()
            _state.value = _state.value.copy(signingOut = false, signedOut = true)
        }
    }

    fun deleteAccount() {
        screenModelScope.launch {
            _state.value = _state.value.copy(deletingAccount = true, deleteError = null)
            runCatching { deviceTokenRepository.deleteToken("android") }
            when (val result = authRepository.deleteAccount()) {
                is AuthResult.Success -> _state.value = _state.value.copy(deletingAccount = false, signedOut = true)
                is AuthResult.Error -> _state.value = _state.value.copy(
                    deletingAccount = false,
                    deleteError = result.message,
                )
            }
        }
    }

    fun clearDeleteError() {
        _state.value = _state.value.copy(deleteError = null)
    }
}
