package org.mycarcompanion.app.ui.mechanics

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.MaintenanceFormData
import org.mycarcompanion.app.data.models.MechanicJob
import org.mycarcompanion.app.data.models.MechanicJobLog
import org.mycarcompanion.app.data.models.MechanicJobLogInsert
import org.mycarcompanion.app.data.repository.AuthRepository
import org.mycarcompanion.app.data.repository.MechanicJobRepository
import org.mycarcompanion.app.data.repository.ProfileRepository

data class MechanicJobDetailState(
    val job: MechanicJob? = null,
    val logs: List<MechanicJobLog> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isCompleting: Boolean = false,
    val completed: Boolean = false,
    val showAddLog: Boolean = false,
    val logForm: MaintenanceFormData = MaintenanceFormData(),
    val isSavingLog: Boolean = false,
    val logError: String? = null,
    val isSendingInvite: Boolean = false,
    val inviteMessage: String? = null,
    val mechanicShopName: String? = null,
    val editingLog: MechanicJobLog? = null,
    val editLogForm: MaintenanceFormData = MaintenanceFormData(),
    val isUpdatingLog: Boolean = false,
    val editLogError: String? = null,
)

class MechanicJobDetailScreenModel(
    private val jobRepository: MechanicJobRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(MechanicJobDetailState())
    val state: StateFlow<MechanicJobDetailState> = _state.asStateFlow()

    fun load(jobId: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val jobDeferred = async { jobRepository.getJobById(jobId) }
            val profileDeferred = async { profileRepository.getMyMechanicProfile() }

            val job = jobDeferred.await().getOrNull()
            val profile = profileDeferred.await().getOrNull()

            if (job == null) {
                _state.value = _state.value.copy(isLoading = false, error = "Job not found")
                return@launch
            }

            val logsResult = jobRepository.getLogsForJob(jobId)
            _state.value = _state.value.copy(
                job = job,
                logs = logsResult.getOrNull() ?: emptyList(),
                isLoading = false,
                mechanicShopName = profile?.shopName,
            )
        }
    }

    fun completeJob(jobId: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(isCompleting = true)
            jobRepository.completeJob(jobId, _state.value.job?.clientEmail)
                .onSuccess {
                    _state.value = _state.value.copy(isCompleting = false, completed = true)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isCompleting = false,
                        error = e.message ?: "Failed to complete job",
                    )
                }
        }
    }

    fun showAddLog() {
        _state.value = _state.value.copy(showAddLog = true, logForm = MaintenanceFormData(), logError = null)
    }

    fun hideAddLog() {
        _state.value = _state.value.copy(showAddLog = false, logError = null)
    }

    fun updateLogForm(form: MaintenanceFormData) {
        _state.value = _state.value.copy(logForm = form, logError = null)
    }

    fun saveLog(jobId: String) {
        val form = _state.value.logForm
        if (form.category.isBlank() || form.description.isBlank() || form.date.isBlank()) {
            _state.value = _state.value.copy(logError = "Category, description, and date are required")
            return
        }
        val mileage = form.mileage.toIntOrNull()
        if (mileage == null) {
            _state.value = _state.value.copy(logError = "Mileage must be a number")
            return
        }
        screenModelScope.launch {
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                _state.value = _state.value.copy(logError = "Not authenticated — please sign in again")
                return@launch
            }
            _state.value = _state.value.copy(isSavingLog = true, logError = null)
            val insert = MechanicJobLogInsert(
                mechanicJobId = jobId,
                mechanicUserId = userId,
                category = form.category,
                description = form.description.trim(),
                date = form.date.trim(),
                mileage = mileage,
                cost = form.cost.toDoubleOrNull(),
                notes = form.notes.trim().ifBlank { null },
            )
            jobRepository.addLog(insert, _state.value.job?.clientEmail)
                .onSuccess { log ->
                    _state.value = _state.value.copy(
                        isSavingLog = false,
                        showAddLog = false,
                        logs = listOf(log) + _state.value.logs,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isSavingLog = false,
                        logError = e.message ?: "Failed to save log",
                    )
                }
        }
    }

    fun deleteLog(logId: String) {
        screenModelScope.launch {
            jobRepository.deleteLog(logId)
                .onSuccess {
                    _state.value = _state.value.copy(
                        logs = _state.value.logs.filter { it.id != logId },
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to delete log")
                }
        }
    }

    fun sendInvite() {
        val job = _state.value.job ?: return
        val email = job.clientEmail ?: return
        val mechanicName = _state.value.mechanicShopName ?: "Your Mechanic"
        val vehicleInfo = "${job.vehicleYear} ${job.vehicleMake} ${job.vehicleModel}"

        screenModelScope.launch {
            _state.value = _state.value.copy(isSendingInvite = true, inviteMessage = null)
            jobRepository.sendInvite(
                jobId = job.id,
                clientEmail = email,
                clientName = job.clientName,
                mechanicName = mechanicName,
                vehicleInfo = vehicleInfo,
            )
                .onSuccess {
                    _state.value = _state.value.copy(
                        isSendingInvite = false,
                        inviteMessage = "Invite sent to $email",
                        job = _state.value.job?.copy(inviteSent = true),
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isSendingInvite = false,
                        inviteMessage = "Failed to send invite: ${e.message}",
                    )
                }
        }
    }

    fun clearInviteMessage() {
        _state.value = _state.value.copy(inviteMessage = null)
    }

    fun showEditLog(log: MechanicJobLog) {
        _state.value = _state.value.copy(
            editingLog = log,
            editLogForm = MaintenanceFormData(
                category = log.category,
                description = log.description,
                date = log.date,
                mileage = log.mileage.toString(),
                cost = log.cost?.toString() ?: "",
                notes = log.notes ?: "",
            ),
            editLogError = null,
        )
    }

    fun hideEditLog() {
        _state.value = _state.value.copy(editingLog = null, editLogError = null)
    }

    fun updateEditLogForm(form: MaintenanceFormData) {
        _state.value = _state.value.copy(editLogForm = form, editLogError = null)
    }

    fun saveEditLog(jobId: String) {
        val oldLog = _state.value.editingLog ?: return
        val form = _state.value.editLogForm
        if (form.category.isBlank() || form.description.isBlank() || form.date.isBlank()) {
            _state.value = _state.value.copy(editLogError = "Category, description, and date are required")
            return
        }
        val mileage = form.mileage.toIntOrNull()
        if (mileage == null) {
            _state.value = _state.value.copy(editLogError = "Mileage must be a number")
            return
        }
        val editNotes = buildEditNotes(oldLog, form, mileage)
        screenModelScope.launch {
            _state.value = _state.value.copy(isUpdatingLog = true, editLogError = null)
            jobRepository.updateLog(
                logId = oldLog.id,
                category = form.category,
                description = form.description.trim(),
                date = form.date.trim(),
                mileage = mileage,
                cost = form.cost.toDoubleOrNull(),
                notes = form.notes.trim().ifBlank { null },
                editNotes = editNotes,
                clientEmail = _state.value.job?.clientEmail,
            )
                .onSuccess { updatedLog ->
                    _state.value = _state.value.copy(
                        isUpdatingLog = false,
                        editingLog = null,
                        logs = _state.value.logs.map { if (it.id == oldLog.id) updatedLog else it },
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isUpdatingLog = false,
                        editLogError = e.message ?: "Failed to update log",
                    )
                }
        }
    }

    private fun buildEditNotes(old: MechanicJobLog, form: MaintenanceFormData, newMileage: Int): String? {
        val changes = mutableListOf<String>()
        if (old.category != form.category) changes.add("category: \"${old.category}\" → \"${form.category}\"")
        if (old.description != form.description.trim()) changes.add("description updated")
        if (old.date != form.date.trim()) changes.add("date: ${old.date} → ${form.date.trim()}")
        if (old.mileage != newMileage) changes.add("mileage: ${old.mileage} → $newMileage")
        val newCost = form.cost.toDoubleOrNull()
        if (old.cost != newCost) {
            val oldStr = old.cost?.let { "\$${it}" } ?: "none"
            val newStr = newCost?.let { "\$${it}" } ?: "none"
            changes.add("cost: $oldStr → $newStr")
        }
        val newNotes = form.notes.trim().ifBlank { null }
        if (old.notes != newNotes) changes.add("notes updated")
        return changes.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }
}
