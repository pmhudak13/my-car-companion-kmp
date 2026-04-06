package org.mycarcompanion.app.ui.auth

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.AuthResult
import org.mycarcompanion.app.data.repository.AuthRepository

data class AuthUiState(
    val isLoading: Boolean = false,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class AuthScreenModel(private val authRepository: AuthRepository) : ScreenModel {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
    }

    fun onConfirmPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = password, errorMessage = null)
    }

    fun signIn() {
        screenModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.signIn(_uiState.value.email, _uiState.value.password)
            _uiState.value = when (result) {
                is AuthResult.Success -> _uiState.value.copy(isLoading = false)
                is AuthResult.Error -> _uiState.value.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }

    fun signInWithGoogle() {
        screenModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.signInWithGoogle()
            _uiState.value = when (result) {
                is AuthResult.Success -> _uiState.value.copy(isLoading = false)
                is AuthResult.Error -> _uiState.value.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }

    fun signUp() {
        val state = _uiState.value
        if (state.password != state.confirmPassword) {
            _uiState.value = state.copy(errorMessage = "Passwords do not match")
            return
        }
        screenModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.signUp(state.email, state.password)
            _uiState.value = when (result) {
                is AuthResult.Success -> _uiState.value.copy(
                    isLoading = false,
                    successMessage = "Check your email to confirm your account"
                )
                is AuthResult.Error -> _uiState.value.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }
}
