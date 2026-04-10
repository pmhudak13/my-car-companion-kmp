package org.mycarcompanion.app.ui.settings

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.repository.AuthRepository

data class SettingsUiState(
    val signingOut: Boolean = false,
    val signedOut: Boolean = false,
)

class SettingsScreenModel(
    private val authRepository: AuthRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun signOut() {
        screenModelScope.launch {
            _state.value = _state.value.copy(signingOut = true)
            authRepository.signOut()
            _state.value = _state.value.copy(signingOut = false, signedOut = true)
        }
    }
}
