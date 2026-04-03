package org.mycarcompanion.app.ui.vehicles

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.MaintenanceLog
import org.mycarcompanion.app.data.models.Reminder
import org.mycarcompanion.app.data.models.Vehicle
import org.mycarcompanion.app.data.repository.MaintenanceRepository
import org.mycarcompanion.app.data.repository.ReminderRepository
import org.mycarcompanion.app.data.repository.VehicleRepository

data class VehicleDetailState(
    val vehicle: Vehicle? = null,
    val logs: List<MaintenanceLog> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val deleted: Boolean = false,
)

class VehicleDetailScreenModel(
    private val vehicleRepository: VehicleRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val reminderRepository: ReminderRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(VehicleDetailState())
    val state: StateFlow<VehicleDetailState> = _state.asStateFlow()

    fun load(vehicleId: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            vehicleRepository.getVehicle(vehicleId)
                .onSuccess { vehicle ->
                    _state.value = _state.value.copy(vehicle = vehicle, isLoading = false)
                    loadLogs(vehicleId)
                    loadReminders(vehicleId)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        error = e.message ?: "Failed to load vehicle",
                        isLoading = false,
                    )
                }
        }
    }

    fun loadLogs(vehicleId: String) {
        screenModelScope.launch {
            maintenanceRepository.getLogsForVehicle(vehicleId)
                .onSuccess { logs ->
                    _state.value = _state.value.copy(logs = logs)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to load logs")
                }
        }
    }

    private fun loadReminders(vehicleId: String) {
        screenModelScope.launch {
            reminderRepository.getRemindersForVehicle(vehicleId)
                .onSuccess { reminders ->
                    _state.value = _state.value.copy(reminders = reminders)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to load reminders")
                }
        }
    }

    fun deleteReminder(reminderId: String) {
        val vehicleId = _state.value.vehicle?.id ?: return
        screenModelScope.launch {
            reminderRepository.deleteReminder(reminderId)
                .onSuccess { loadReminders(vehicleId) }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to delete reminder")
                }
        }
    }

    fun deleteVehicle() {
        val vehicleId = _state.value.vehicle?.id ?: return
        screenModelScope.launch {
            vehicleRepository.deleteVehicle(vehicleId)
                .onSuccess { _state.value = _state.value.copy(deleted = true) }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to delete")
                }
        }
    }

    fun deleteLog(logId: String) {
        val vehicleId = _state.value.vehicle?.id ?: return
        screenModelScope.launch {
            maintenanceRepository.deleteLog(logId)
                .onSuccess { loadLogs(vehicleId) }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to delete log")
                }
        }
    }
}
