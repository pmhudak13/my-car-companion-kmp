package org.mycarcompanion.app.ui.mechanics

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.MechanicAssignment
import org.mycarcompanion.app.data.models.MechanicProfile
import org.mycarcompanion.app.data.repository.MechanicAssignmentRepository
import org.mycarcompanion.app.data.repository.ProfileRepository

data class MechanicDashboardState(
    val assignments: List<MechanicAssignment> = emptyList(),
    val profile: MechanicProfile? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val completingId: String? = null,
)

class MechanicDashboardScreenModel(
    private val assignmentRepo: MechanicAssignmentRepository,
    private val profileRepo: ProfileRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(MechanicDashboardState())
    val state: StateFlow<MechanicDashboardState> = _state.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val assignmentsDeferred = async { assignmentRepo.getAssignmentsForMechanic() }
            val profileDeferred = async { profileRepo.getMyMechanicProfile() }

            val assignmentsResult = assignmentsDeferred.await()
            val profileResult = profileDeferred.await()

            val assignments = assignmentsResult.getOrNull() ?: emptyList()
            val profile = profileResult.getOrNull()
            val error = assignmentsResult.exceptionOrNull()?.message
                ?: profileResult.exceptionOrNull()?.message

            _state.value = _state.value.copy(
                assignments = assignments,
                profile = profile,
                isLoading = false,
                error = error,
            )
        }
    }

    fun completeJob(assignmentId: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(completingId = assignmentId)
            assignmentRepo.completeAssignment(assignmentId)
                .onSuccess {
                    _state.value = _state.value.copy(
                        assignments = _state.value.assignments.filter { it.id != assignmentId },
                        completingId = null,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        completingId = null,
                        error = e.message ?: "Failed to complete job",
                    )
                }
        }
    }

    fun refresh() {
        loadData()
    }
}
