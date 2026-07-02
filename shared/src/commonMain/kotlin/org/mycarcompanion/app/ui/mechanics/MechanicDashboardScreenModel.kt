package org.mycarcompanion.app.ui.mechanics

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.MechanicAssignment
import org.mycarcompanion.app.data.models.MechanicJob
import org.mycarcompanion.app.data.models.MechanicProfile
import org.mycarcompanion.app.data.repository.MechanicAssignmentRepository
import org.mycarcompanion.app.data.repository.MechanicJobRepository
import org.mycarcompanion.app.data.repository.ProfileRepository

enum class MechanicDashboardTab { CLIENT_JOBS, MY_JOBS }

data class MechanicDashboardState(
    val selectedTab: MechanicDashboardTab = MechanicDashboardTab.CLIENT_JOBS,
    val assignments: List<MechanicAssignment> = emptyList(),
    val myJobs: List<MechanicJob> = emptyList(),
    val profile: MechanicProfile? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val completingId: String? = null,
)

class MechanicDashboardScreenModel(
    private val assignmentRepo: MechanicAssignmentRepository,
    private val jobRepo: MechanicJobRepository,
    private val profileRepo: ProfileRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(MechanicDashboardState())
    val state: StateFlow<MechanicDashboardState> = _state.asStateFlow()

    fun selectTab(tab: MechanicDashboardTab) {
        _state.value = _state.value.copy(selectedTab = tab, error = null)
    }

    // Re-triggered from Content() on every return; silent once data is loaded
    private fun loadData() {
        screenModelScope.launch {
            val silent = _state.value.profile != null ||
                _state.value.assignments.isNotEmpty() ||
                _state.value.myJobs.isNotEmpty()
            if (!silent) {
                _state.value = _state.value.copy(isLoading = true, error = null)
            }
            val assignmentsDeferred = async { assignmentRepo.getAssignmentsForMechanic() }
            val myJobsDeferred = async { jobRepo.getMyJobs() }
            val profileDeferred = async { profileRepo.getMyMechanicProfile() }

            val assignmentsResult = assignmentsDeferred.await()
            val myJobsResult = myJobsDeferred.await()
            val profileResult = profileDeferred.await()

            _state.value = _state.value.copy(
                // Keep stale data if a silent refresh fails
                assignments = assignmentsResult.getOrNull() ?: _state.value.assignments,
                myJobs = myJobsResult.getOrNull() ?: _state.value.myJobs,
                profile = profileResult.getOrNull() ?: _state.value.profile,
                isLoading = false,
                error = if (silent) null else assignmentsResult.exceptionOrNull()?.message
                    ?: myJobsResult.exceptionOrNull()?.message
                    ?: profileResult.exceptionOrNull()?.message,
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
