package org.mycarcompanion.app.ui.admin

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.AdminUserEntry
import org.mycarcompanion.app.data.repository.ProfileRepository

data class AdminUiState(
    val users: List<AdminUserEntry> = emptyList(),
    val isLoading: Boolean = true,
    val actionUserId: String? = null,
    val error: String? = null,
    val successMessage: String? = null,
)

class AdminScreenModel(
    private val profileRepository: ProfileRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    init {
        loadUsers()
    }

    fun loadUsers() {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            profileRepository.getAllUsers()
                .onSuccess { users ->
                    _state.value = _state.value.copy(
                        users = users.sortedBy { it.email },
                        isLoading = false,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        error = e.message ?: "Failed to load users",
                        isLoading = false,
                    )
                }
        }
    }

    fun giftPremium(userId: String, reason: String?) {
        screenModelScope.launch {
            _state.value = _state.value.copy(actionUserId = userId, error = null, successMessage = null)
            profileRepository.giftPremium(userId, reason)
                .onSuccess {
                    _state.value = _state.value.copy(actionUserId = null, successMessage = "Premium gifted!")
                    loadUsers()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        actionUserId = null,
                        error = e.message ?: "Failed to gift premium",
                    )
                }
        }
    }

    fun revokePremium(userId: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(actionUserId = userId, error = null, successMessage = null)
            profileRepository.revokePremium(userId)
                .onSuccess {
                    _state.value = _state.value.copy(actionUserId = null, successMessage = "Premium revoked")
                    loadUsers()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        actionUserId = null,
                        error = e.message ?: "Failed to revoke premium",
                    )
                }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(successMessage = null, error = null)
    }
}
