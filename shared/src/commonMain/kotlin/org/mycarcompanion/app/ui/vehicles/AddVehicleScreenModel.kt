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

data class AddVehicleState(
    val form: VehicleFormData = VehicleFormData(),
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class AddVehicleScreenModel(
    private val vehicleRepository: VehicleRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(AddVehicleState())
    val state: StateFlow<AddVehicleState> = _state.asStateFlow()

    fun updateForm(form: VehicleFormData) {
        _state.value = _state.value.copy(form = form)
    }

    fun save() {
        val form = _state.value.form
        val yearInt = form.year.toIntOrNull()
        if (form.make.isBlank() || form.model.isBlank() || yearInt == null) {
            _state.value = _state.value.copy(error = "Make, model, and a valid year are required")
            return
        }

        screenModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            val vehicle = Vehicle(
                make = form.make.trim(),
                model = form.model.trim(),
                year = yearInt,
                color = form.color.trim().ifBlank { null },
                licensePlate = form.licensePlate.trim().ifBlank { null },
                vin = form.vin.trim().ifBlank { null },
                odometer = form.odometer.toIntOrNull() ?: 0,
                unit = form.unit,
            )
            vehicleRepository.addVehicle(vehicle)
                .onSuccess {
                    _state.value = _state.value.copy(isSaving = false, saved = true)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to save vehicle",
                    )
                }
        }
    }
}
