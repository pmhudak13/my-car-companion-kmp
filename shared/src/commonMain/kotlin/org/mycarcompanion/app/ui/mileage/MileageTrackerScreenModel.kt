package org.mycarcompanion.app.ui.mileage

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.MileageTrip
import org.mycarcompanion.app.data.models.MileageTripFormData
import org.mycarcompanion.app.data.models.Vehicle
import org.mycarcompanion.app.data.repository.MileageTripRepository
import org.mycarcompanion.app.data.repository.VehicleRepository
import org.mycarcompanion.app.platform.getCurrentPosition

data class MileageTrackerState(
    val vehicles: List<Vehicle> = emptyList(),
    val trips: List<MileageTrip> = emptyList(),
    val activeTrip: MileageTrip? = null,
    val form: MileageTripFormData = MileageTripFormData(),
    val isLoading: Boolean = true,
    val isStarting: Boolean = false,
    val isEnding: Boolean = false,
    val endMiles: String = "",
    val error: String? = null,
)

class MileageTrackerScreenModel(
    private val tripRepository: MileageTripRepository,
    private val vehicleRepository: VehicleRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(MileageTrackerState())
    val state: StateFlow<MileageTrackerState> = _state.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            vehicleRepository.getVehicles()
                .onSuccess { vehicles ->
                    _state.value = _state.value.copy(vehicles = vehicles)
                }
            tripRepository.getTrips()
                .onSuccess { trips ->
                    val active = trips.firstOrNull { it.endedAt == null }
                    _state.value = _state.value.copy(
                        trips = trips,
                        activeTrip = active,
                        isLoading = false,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        error = e.message ?: "Failed to load trips",
                        isLoading = false,
                    )
                }
        }
    }

    fun updateForm(form: MileageTripFormData) {
        _state.value = _state.value.copy(form = form)
    }

    fun updateEndMiles(miles: String) {
        _state.value = _state.value.copy(endMiles = miles)
    }

    fun startTrip() {
        val form = _state.value.form
        if (form.purpose.isBlank()) {
            _state.value = _state.value.copy(error = "Select a trip purpose")
            return
        }

        screenModelScope.launch {
            _state.value = _state.value.copy(isStarting = true, error = null)
            val pos = getCurrentPosition()
            val vehicleId = form.vehicleId.ifBlank { null }
            tripRepository.startTrip(
                vehicleId = vehicleId,
                purpose = form.purpose,
                notes = form.notes.trim().ifBlank { null },
                startLat = pos?.latitude,
                startLng = pos?.longitude,
            )
                .onSuccess {
                    _state.value = _state.value.copy(isStarting = false)
                    loadData()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isStarting = false,
                        error = e.message ?: "Failed to start trip",
                    )
                }
        }
    }

    fun endTrip() {
        val trip = _state.value.activeTrip ?: return
        val miles = _state.value.endMiles.toDoubleOrNull()
        if (miles == null || miles <= 0) {
            _state.value = _state.value.copy(error = "Enter miles driven")
            return
        }

        screenModelScope.launch {
            _state.value = _state.value.copy(isEnding = true, error = null)
            val pos = getCurrentPosition()
            tripRepository.endTrip(
                tripId = trip.id,
                distanceMiles = miles,
                endLat = pos?.latitude,
                endLng = pos?.longitude,
            )
                .onSuccess {
                    _state.value = _state.value.copy(isEnding = false, endMiles = "")
                    loadData()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isEnding = false,
                        error = e.message ?: "Failed to end trip",
                    )
                }
        }
    }

    fun deleteTrip(tripId: String) {
        screenModelScope.launch {
            tripRepository.deleteTrip(tripId)
                .onSuccess { loadData() }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to delete trip")
                }
        }
    }
}
