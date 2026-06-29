package org.mycarcompanion.app.ui.maintenance

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.MaintenanceFormData
import org.mycarcompanion.app.data.models.MaintenanceLog
import org.mycarcompanion.app.data.repository.MaintenanceRepository
import org.mycarcompanion.app.data.repository.VehicleRepository

data class AddMaintenanceState(
    val form: MaintenanceFormData = MaintenanceFormData(),
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val currentOdometer: Int? = null,
    val updateOdometer: Boolean = true,
)

class AddMaintenanceScreenModel(
    private val maintenanceRepository: MaintenanceRepository,
    private val vehicleRepository: VehicleRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(AddMaintenanceState())
    val state: StateFlow<AddMaintenanceState> = _state.asStateFlow()

    fun load(vehicleId: String) {
        if (_state.value.currentOdometer != null) return
        screenModelScope.launch {
            vehicleRepository.getVehicle(vehicleId).onSuccess {
                _state.value = _state.value.copy(currentOdometer = it.odometer)
            }
        }
    }

    fun setUpdateOdometer(value: Boolean) {
        _state.value = _state.value.copy(updateOdometer = value)
    }

    fun updateForm(form: MaintenanceFormData) {
        _state.value = _state.value.copy(form = form)
    }

    fun save(vehicleId: String) {
        val form = _state.value.form
        if (form.category.isBlank() || form.description.isBlank() || form.date.isBlank()) {
            _state.value = _state.value.copy(error = "Category, description, and date are required")
            return
        }
        val mileageInt = form.mileage.toIntOrNull()
        if (mileageInt == null) {
            _state.value = _state.value.copy(error = "Mileage must be a number")
            return
        }

        screenModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            val log = MaintenanceLog(
                vehicleId = vehicleId,
                category = form.category,
                description = form.description.trim(),
                date = form.date.trim(),
                mileage = mileageInt,
                cost = form.cost.toDoubleOrNull(),
                notes = form.notes.trim().ifBlank { null },
            )
            maintenanceRepository.addLog(log)
                .onSuccess {
                    // ponytail: best-effort odometer sync; a failed bump never blocks the saved log.
                    val odo = _state.value.currentOdometer
                    if (_state.value.updateOdometer && odo != null && mileageInt > odo) {
                        vehicleRepository.updateOdometer(vehicleId, mileageInt)
                    }
                    _state.value = _state.value.copy(isSaving = false, saved = true)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to save",
                    )
                }
        }
    }
}
