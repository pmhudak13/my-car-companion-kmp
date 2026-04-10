package org.mycarcompanion.app.ui.vehicles

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.Vehicle
import org.mycarcompanion.app.data.models.VehicleFormData
import org.mycarcompanion.app.data.repository.VehicleRepository

data class VehicleSettingsState(
    val vehicle: Vehicle? = null,
    val form: VehicleFormData = VehicleFormData(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val deleting: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val showDeleteConfirm: Boolean = false,
)

class VehicleSettingsScreenModel(
    private val vehicleRepository: VehicleRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(VehicleSettingsState())
    val state: StateFlow<VehicleSettingsState> = _state.asStateFlow()

    fun load(vehicleId: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            vehicleRepository.getVehicle(vehicleId)
                .onSuccess { vehicle ->
                    _state.value = _state.value.copy(
                        loading = false,
                        vehicle = vehicle,
                        form = VehicleFormData(
                            make = vehicle.make,
                            model = vehicle.model,
                            year = vehicle.year.toString(),
                            color = vehicle.color ?: "",
                            licensePlate = vehicle.licensePlate ?: "",
                            vin = vehicle.vin ?: "",
                            odometer = vehicle.odometer.toString(),
                            unit = vehicle.unit,
                        ),
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "Failed to load vehicle",
                    )
                }
        }
    }

    fun updateForm(form: VehicleFormData) {
        _state.value = _state.value.copy(form = form, saved = false)
    }

    fun save() {
        val vehicle = _state.value.vehicle ?: return
        val form = _state.value.form
        val yearInt = form.year.toIntOrNull()
        if (form.make.isBlank() || form.model.isBlank() || yearInt == null) {
            _state.value = _state.value.copy(error = "Make, model, and a valid year are required")
            return
        }
        screenModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            val updated = vehicle.copy(
                make = form.make.trim(),
                model = form.model.trim(),
                year = yearInt,
                color = form.color.trim().ifBlank { null },
                licensePlate = form.licensePlate.trim().ifBlank { null },
                vin = form.vin.trim().ifBlank { null },
                odometer = form.odometer.toIntOrNull() ?: vehicle.odometer,
                unit = form.unit,
            )
            vehicleRepository.updateVehicle(updated)
                .onSuccess { _state.value = _state.value.copy(saving = false, saved = true) }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        saving = false,
                        error = e.message ?: "Failed to save",
                    )
                }
        }
    }

    fun showDeleteConfirm() {
        _state.value = _state.value.copy(showDeleteConfirm = true)
    }

    fun cancelDelete() {
        _state.value = _state.value.copy(showDeleteConfirm = false)
    }

    fun deleteVehicle() {
        val vehicleId = _state.value.vehicle?.id ?: return
        screenModelScope.launch {
            _state.value = _state.value.copy(deleting = true, showDeleteConfirm = false)
            vehicleRepository.deleteVehicle(vehicleId)
                .onSuccess { _state.value = _state.value.copy(deleting = false, deleted = true) }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        deleting = false,
                        error = e.message ?: "Failed to delete vehicle",
                    )
                }
        }
    }
}
