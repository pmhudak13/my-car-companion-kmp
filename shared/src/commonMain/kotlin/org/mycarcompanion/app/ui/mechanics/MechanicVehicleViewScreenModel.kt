package org.mycarcompanion.app.ui.mechanics

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.MaintenanceLog
import org.mycarcompanion.app.data.models.MechanicAssignment
import org.mycarcompanion.app.data.models.Vehicle
import org.mycarcompanion.app.data.repository.MaintenanceRepository
import org.mycarcompanion.app.data.repository.MechanicAssignmentRepository
import org.mycarcompanion.app.data.repository.VehicleRepository

data class MechanicVehicleViewState(
    val vehicle: Vehicle? = null,
    val assignment: MechanicAssignment? = null,
    val logs: List<MaintenanceLog> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val completing: Boolean = false,
    val completed: Boolean = false,
)

class MechanicVehicleViewScreenModel(
    private val vehicleRepository: VehicleRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val assignmentRepository: MechanicAssignmentRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(MechanicVehicleViewState())
    val state: StateFlow<MechanicVehicleViewState> = _state.asStateFlow()

    fun load(vehicleId: String, assignmentId: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val vehicleResult = vehicleRepository.getVehicle(vehicleId)
            val logsResult = maintenanceRepository.getLogsForVehicle(vehicleId)
            val assignmentsResult = assignmentRepository.getAssignmentsForVehicle(vehicleId)

            val assignment = assignmentsResult.getOrDefault(emptyList())
                .find { it.id == assignmentId }

            _state.value = _state.value.copy(
                loading = false,
                vehicle = vehicleResult.getOrNull(),
                logs = logsResult.getOrDefault(emptyList()),
                assignment = assignment,
                error = vehicleResult.exceptionOrNull()?.message,
            )
        }
    }

    fun reloadLogs(vehicleId: String) {
        screenModelScope.launch {
            maintenanceRepository.getLogsForVehicle(vehicleId)
                .onSuccess { logs -> _state.value = _state.value.copy(logs = logs) }
        }
    }

    fun completeJob(assignmentId: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(completing = true)
            assignmentRepository.completeAssignment(assignmentId)
                .onSuccess { _state.value = _state.value.copy(completing = false, completed = true) }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        completing = false,
                        error = e.message ?: "Failed to complete job",
                    )
                }
        }
    }
}
