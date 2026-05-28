package org.mycarcompanion.app.ui.vehicles

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.MaintenanceLog
import org.mycarcompanion.app.data.models.MechanicAssignment
import org.mycarcompanion.app.data.models.MechanicJob
import org.mycarcompanion.app.data.models.MechanicJobIssue
import org.mycarcompanion.app.data.models.MechanicJobMedia
import org.mycarcompanion.app.data.models.Reminder
import org.mycarcompanion.app.data.models.Vehicle
import org.mycarcompanion.app.data.repository.MaintenanceRepository
import org.mycarcompanion.app.data.repository.MechanicAssignmentRepository
import org.mycarcompanion.app.data.repository.MechanicJobIssueRepository
import org.mycarcompanion.app.data.repository.MechanicJobMediaRepository
import org.mycarcompanion.app.data.repository.MechanicJobRepository
import org.mycarcompanion.app.data.repository.ReminderRepository
import org.mycarcompanion.app.data.repository.VehicleRepository

data class VehicleDetailState(
    val vehicle: Vehicle? = null,
    val logs: List<MaintenanceLog> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val assignments: List<MechanicAssignment> = emptyList(),
    val mechanicJobs: List<MechanicJob> = emptyList(),
    val issuesByJobId: Map<String, List<MechanicJobIssue>> = emptyMap(),
    val mediaByJobId: Map<String, List<MechanicJobMedia>> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val deleted: Boolean = false,
    val respondingIssueId: String? = null,
)

class VehicleDetailScreenModel(
    private val vehicleRepository: VehicleRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val reminderRepository: ReminderRepository,
    private val assignmentRepository: MechanicAssignmentRepository,
    private val mechanicJobRepository: MechanicJobRepository,
    private val issueRepository: MechanicJobIssueRepository,
    private val mediaRepository: MechanicJobMediaRepository,
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
                    loadAssignments(vehicleId)
                    loadMechanicJobs(vehicleId)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to load vehicle", isLoading = false)
                }
        }
    }

    fun loadLogs(vehicleId: String) {
        screenModelScope.launch {
            maintenanceRepository.getLogsForVehicle(vehicleId)
                .onSuccess { logs -> _state.value = _state.value.copy(logs = logs) }
                .onFailure { e -> _state.value = _state.value.copy(error = e.message ?: "Failed to load logs") }
        }
    }

    private fun loadReminders(vehicleId: String) {
        screenModelScope.launch {
            reminderRepository.getRemindersForVehicle(vehicleId)
                .onSuccess { reminders -> _state.value = _state.value.copy(reminders = reminders) }
                .onFailure { e -> _state.value = _state.value.copy(error = e.message ?: "Failed to load reminders") }
        }
    }

    private fun loadAssignments(vehicleId: String) {
        screenModelScope.launch {
            assignmentRepository.getAssignmentsForVehicle(vehicleId)
                .onSuccess { assignments -> _state.value = _state.value.copy(assignments = assignments) }
                .onFailure { e -> _state.value = _state.value.copy(error = e.message ?: "Failed to load assignments") }
        }
    }

    private fun loadMechanicJobs(vehicleId: String) {
        screenModelScope.launch {
            val jobsResult = mechanicJobRepository.getJobsForVehicle(vehicleId)
            val jobs = jobsResult.getOrNull() ?: return@launch
            _state.value = _state.value.copy(mechanicJobs = jobs)

            if (jobs.isEmpty()) return@launch
            val jobIds = jobs.map { it.id }

            val issuesDeferred = async { issueRepository.getIssuesForVehicleJobs(jobIds) }
            val mediaDeferred = async { mediaRepository.getMediaForJobs(jobIds) }

            val issues = issuesDeferred.await().getOrNull() ?: emptyList()
            val media = mediaDeferred.await().getOrNull() ?: emptyList()

            _state.value = _state.value.copy(
                issuesByJobId = issues.groupBy { it.mechanicJobId },
                mediaByJobId = media.groupBy { it.mechanicJobId },
            )
        }
    }

    fun respondToIssue(issueId: String, approved: Boolean, response: String?) {
        screenModelScope.launch {
            _state.value = _state.value.copy(respondingIssueId = issueId)
            issueRepository.respondToIssue(issueId, approved, response)
                .onSuccess { updated ->
                    val newMap = _state.value.issuesByJobId.toMutableMap()
                    newMap[updated.mechanicJobId] = newMap[updated.mechanicJobId]
                        ?.map { if (it.id == issueId) updated else it }
                        ?: listOf(updated)
                    _state.value = _state.value.copy(respondingIssueId = null, issuesByJobId = newMap)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        respondingIssueId = null,
                        error = e.message ?: "Failed to respond to issue",
                    )
                }
        }
    }

    fun revokeAssignment(assignmentId: String) {
        val vehicleId = _state.value.vehicle?.id ?: return
        screenModelScope.launch {
            assignmentRepository.revokeAssignment(assignmentId)
                .onSuccess { loadAssignments(vehicleId) }
                .onFailure { e -> _state.value = _state.value.copy(error = e.message ?: "Failed to revoke assignment") }
        }
    }

    fun deleteReminder(reminderId: String) {
        val vehicleId = _state.value.vehicle?.id ?: return
        screenModelScope.launch {
            reminderRepository.deleteReminder(reminderId)
                .onSuccess { loadReminders(vehicleId) }
                .onFailure { e -> _state.value = _state.value.copy(error = e.message ?: "Failed to delete reminder") }
        }
    }

    fun deleteVehicle() {
        val vehicleId = _state.value.vehicle?.id ?: return
        screenModelScope.launch {
            vehicleRepository.deleteVehicle(vehicleId)
                .onSuccess { _state.value = _state.value.copy(deleted = true) }
                .onFailure { e -> _state.value = _state.value.copy(error = e.message ?: "Failed to delete") }
        }
    }

    fun deleteLog(logId: String) {
        val vehicleId = _state.value.vehicle?.id ?: return
        screenModelScope.launch {
            maintenanceRepository.deleteLog(logId)
                .onSuccess { loadLogs(vehicleId) }
                .onFailure { e -> _state.value = _state.value.copy(error = e.message ?: "Failed to delete log") }
        }
    }
}
