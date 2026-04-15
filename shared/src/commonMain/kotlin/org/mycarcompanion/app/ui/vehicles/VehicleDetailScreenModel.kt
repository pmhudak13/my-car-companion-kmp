package org.mycarcompanion.app.ui.vehicles

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.MaintenanceLog
import org.mycarcompanion.app.data.models.MechanicAssignment
import org.mycarcompanion.app.data.models.Reminder
import org.mycarcompanion.app.data.models.Vehicle
import org.mycarcompanion.app.data.repository.MaintenanceRepository
import org.mycarcompanion.app.data.repository.MechanicAssignmentRepository
import org.mycarcompanion.app.data.repository.ReminderRepository
import org.mycarcompanion.app.data.repository.VehicleRepository

data class VehicleDetailState(
    val vehicle: Vehicle? = null,
    val logs: List<MaintenanceLog> = emptyList(),
    val pendingLogs: List<MaintenanceLog> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val assignments: List<MechanicAssignment> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val deleted: Boolean = false,
)

class VehicleDetailScreenModel(
    private val vehicleRepository: VehicleRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val reminderRepository: ReminderRepository,
    private val assignmentRepository: MechanicAssignmentRepository,
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
                    loadPendingLogs(vehicleId)
                    loadReminders(vehicleId)
                    loadAssignments(vehicleId)
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

    private fun loadPendingLogs(vehicleId: String) {
        screenModelScope.launch {
            maintenanceRepository.getPendingLogsForVehicle(vehicleId)
                .onSuccess { pending ->
                    _state.value = _state.value.copy(pendingLogs = pending)
                }
                .onFailure { /* non-critical, ignore */ }
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

    private fun loadAssignments(vehicleId: String) {
        screenModelScope.launch {
            assignmentRepository.getAssignmentsForVehicle(vehicleId)
                .onSuccess { assignments ->
                    _state.value = _state.value.copy(assignments = assignments)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to load assignments")
                }
        }
    }

    fun revokeAssignment(assignmentId: String) {
        val vehicleId = _state.value.vehicle?.id ?: return
        screenModelScope.launch {
            assignmentRepository.revokeAssignment(assignmentId)
                .onSuccess { loadAssignments(vehicleId) }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to revoke assignment")
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

    fun approvePendingLog(logId: String) {
        val vehicleId = _state.value.vehicle?.id ?: return
        screenModelScope.launch {
            maintenanceRepository.approveLog(logId)
                .onSuccess {
                    loadLogs(vehicleId)
                    loadPendingLogs(vehicleId)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to approve record")
                }
        }
    }

    fun rejectPendingLog(logId: String) {
        val vehicleId = _state.value.vehicle?.id ?: return
        screenModelScope.launch {
            maintenanceRepository.rejectLog(logId)
                .onSuccess { loadPendingLogs(vehicleId) }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to reject record")
                }
        }
    }
}
