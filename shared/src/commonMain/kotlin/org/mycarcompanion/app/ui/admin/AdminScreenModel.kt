package org.mycarcompanion.app.ui.admin

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.AdminUserEntry
import org.mycarcompanion.app.data.models.MechanicProfile
import org.mycarcompanion.app.data.repository.ProfileRepository

data class AdminUiState(
    val users: List<AdminUserEntry> = emptyList(),
    val isLoading: Boolean = true,
    val actionUserId: String? = null,
    val error: String? = null,
    val successMessage: String? = null,
    val mechanics: List<MechanicProfile> = emptyList(),
    val mechanicsLoading: Boolean = false,
    val mechanicsError: String? = null,
    val processingMechanicId: String? = null,
)

class AdminScreenModel(
    private val profileRepository: ProfileRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    init {
        loadUsers()
        loadMechanics()
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

    fun loadMechanics() {
        screenModelScope.launch {
            _state.value = _state.value.copy(mechanicsLoading = true, mechanicsError = null)
            profileRepository.getAllMechanicProfiles()
                .onSuccess { mechanics ->
                    _state.value = _state.value.copy(
                        mechanics = mechanics,
                        mechanicsLoading = false,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        mechanicsError = e.message ?: "Failed to load mechanics",
                        mechanicsLoading = false,
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

    fun approveMechanic(userId: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(processingMechanicId = userId)
            profileRepository.approveMechanic(userId)
                .onSuccess {
                    val updated = _state.value.mechanics.map { m ->
                        if (m.userId == userId) m.copy(verificationStatus = "verified") else m
                    }
                    _state.value = _state.value.copy(
                        mechanics = updated,
                        processingMechanicId = null,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        mechanicsError = e.message ?: "Failed to approve mechanic",
                        processingMechanicId = null,
                    )
                }
        }
    }

    fun rejectMechanic(userId: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(processingMechanicId = userId)
            profileRepository.rejectMechanic(userId)
                .onSuccess {
                    val updated = _state.value.mechanics.map { m ->
                        if (m.userId == userId) m.copy(verificationStatus = "rejected") else m
                    }
                    _state.value = _state.value.copy(
                        mechanics = updated,
                        processingMechanicId = null,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        mechanicsError = e.message ?: "Failed to reject mechanic",
                        processingMechanicId = null,
                    )
                }
        }
    }

    fun convertToMechanic(userId: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(actionUserId = userId, error = null, successMessage = null)
            profileRepository.convertToMechanic(userId)
                .onSuccess {
                    _state.value = _state.value.copy(
                        actionUserId = null,
                        successMessage = "User converted to mechanic",
                    )
                    loadUsers()
                    loadMechanics()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        actionUserId = null,
                        error = e.message ?: "Failed to convert user to mechanic",
                    )
                }
        }
    }

    fun revokeMechanicRole(userId: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(actionUserId = userId, error = null, successMessage = null)
            profileRepository.revokeMechanicRole(userId)
                .onSuccess {
                    _state.value = _state.value.copy(
                        actionUserId = null,
                        successMessage = "Mechanic role revoked",
                    )
                    loadUsers()
                    loadMechanics()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        actionUserId = null,
                        error = e.message ?: "Failed to revoke mechanic role",
                    )
                }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(successMessage = null, error = null)
    }
}
