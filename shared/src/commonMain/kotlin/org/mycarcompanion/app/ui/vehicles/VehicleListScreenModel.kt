package org.mycarcompanion.app.ui.vehicles

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.Vehicle
import org.mycarcompanion.app.data.repository.VehicleRepository

data class VehicleListState(
    val vehicles: List<Vehicle> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class VehicleListScreenModel(
    private val vehicleRepository: VehicleRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(VehicleListState())
    val state: StateFlow<VehicleListState> = _state.asStateFlow()

    init {
        loadVehicles()
    }

    fun loadVehicles() {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            vehicleRepository.getVehicles()
                .onSuccess { vehicles ->
                    _state.value = _state.value.copy(vehicles = vehicles, isLoading = false)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        error = e.message ?: "Failed to load vehicles",
                        isLoading = false,
                    )
                }
        }
    }

    fun deleteVehicle(id: String) {
        screenModelScope.launch {
            vehicleRepository.deleteVehicle(id)
                .onSuccess { loadVehicles() }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to delete vehicle")
                }
        }
    }
}
