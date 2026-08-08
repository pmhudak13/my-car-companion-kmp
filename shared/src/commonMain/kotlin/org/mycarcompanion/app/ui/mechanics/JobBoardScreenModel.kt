package org.mycarcompanion.app.ui.mechanics

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.JobRequest
import org.mycarcompanion.app.data.models.Vehicle
import org.mycarcompanion.app.data.repository.JobRequestRepository
import org.mycarcompanion.app.data.repository.VehicleRepository

data class JobBoardState(
    val requests: List<JobRequest> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isPosting: Boolean = false,
    val postError: String? = null,
)

class JobBoardScreenModel(
    private val jobRequestRepository: JobRequestRepository,
    private val vehicleRepository: VehicleRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(JobBoardState())
    val state: StateFlow<JobBoardState> = _state.asStateFlow()

    fun load(asMechanic: Boolean) {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result =
                if (asMechanic) jobRequestRepository.getOpenRequests()
                else jobRequestRepository.getMyRequests()
            result
                .onSuccess { requests ->
                    _state.value = _state.value.copy(requests = requests, isLoading = false)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load requests",
                    )
                }
            if (!asMechanic) {
                vehicleRepository.getVehicles().onSuccess {
                    _state.value = _state.value.copy(vehicles = it)
                }
            }
        }
    }

    fun post(vehicle: Vehicle, title: String, description: String, city: String, state: String) {
        if (title.isBlank()) return
        screenModelScope.launch {
            _state.value = _state.value.copy(isPosting = true, postError = null)
            jobRequestRepository.postRequest(
                title = title,
                description = description,
                vehicleLabel = "${vehicle.year} ${vehicle.make} ${vehicle.model}",
                vehicleId = vehicle.id,
                city = city,
                state = state,
            )
                .onSuccess {
                    _state.value = _state.value.copy(isPosting = false)
                    load(asMechanic = false)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isPosting = false,
                        postError = e.message ?: "Failed to post request",
                    )
                }
        }
    }

    fun close(id: String) {
        screenModelScope.launch {
            jobRequestRepository.closeRequest(id).onSuccess { load(asMechanic = false) }
        }
    }
}
