package org.mycarcompanion.app.ui.home

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.AuthResult
import org.mycarcompanion.app.data.models.AuthState
import org.mycarcompanion.app.data.models.Vehicle
import org.mycarcompanion.app.data.repository.AuthRepository
import org.mycarcompanion.app.data.repository.VehicleRepository

data class VehicleUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class HomeScreenModel(
    private val authRepository: AuthRepository,
    private val vehicleRepository: VehicleRepository,
) : ScreenModel {

    val authState = authRepository.authState.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthState.Loading,
    )

    private val _vehicleState = MutableStateFlow(VehicleUiState())
    val vehicleState: StateFlow<VehicleUiState> = _vehicleState.asStateFlow()

    private val _linkState = MutableStateFlow<AuthResult?>(null)
    val linkState: StateFlow<AuthResult?> = _linkState.asStateFlow()

    // Called from Content() via LaunchedEffect(Unit), so it re-runs every time the
    // screen returns to the front of the stack. When data is already loaded the
    // refresh is silent: stale data stays visible while the new list loads.
    fun refresh() {
        screenModelScope.launch {
            val silent = _vehicleState.value.vehicles.isNotEmpty()
            if (!silent) {
                _vehicleState.value = _vehicleState.value.copy(isLoading = true, error = null)
            }
            vehicleRepository.getVehicles()
                .onSuccess { vehicles ->
                    _vehicleState.value = _vehicleState.value.copy(
                        vehicles = vehicles,
                        isLoading = false,
                        error = null,
                    )
                }
                .onFailure { e ->
                    // On a silent refresh keep showing the stale list instead of an error
                    if (!silent) {
                        _vehicleState.value = _vehicleState.value.copy(
                            error = e.message ?: "Failed to load vehicles",
                            isLoading = false,
                        )
                    }
                }
        }
    }

    fun linkGoogleAccount() {
        screenModelScope.launch {
            _linkState.value = authRepository.linkGoogleIdentity()
        }
    }

    fun clearLinkState() {
        _linkState.value = null
    }

    fun signOut() {
        screenModelScope.launch {
            authRepository.signOut()
        }
    }
}
