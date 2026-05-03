package org.mycarcompanion.app.ui.mechanics

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.MechanicProfile
import org.mycarcompanion.app.data.repository.MechanicAssignmentRepository
import org.mycarcompanion.app.data.repository.MechanicRepository

data class MechanicDirectoryState(
    val allMechanics: List<MechanicProfile> = emptyList(),
    val mechanics: List<MechanicProfile> = emptyList(),
    val searchQuery: String = "",
    val assignedMechanicIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val assigningMechanicId: String? = null,
    val assignSuccess: Boolean = false,
    val assignError: String? = null,
)

class MechanicDirectoryScreenModel(
    private val mechanicRepository: MechanicRepository,
    private val assignmentRepository: MechanicAssignmentRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(MechanicDirectoryState())
    val state: StateFlow<MechanicDirectoryState> = _state.asStateFlow()

    init {
        loadMechanics(null)
    }

    fun loadMechanics(vehicleId: String? = null) {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val mechanicsResult = mechanicRepository.getVerifiedMechanics()
            val assignedIds = if (vehicleId != null) {
                assignmentRepository.getAssignmentsForVehicle(vehicleId)
                    .getOrNull()
                    ?.map { it.mechanicUserId }
                    ?.toSet()
                    ?: emptySet()
            } else {
                emptySet()
            }
            mechanicsResult
                .onSuccess { mechanics ->
                    _state.value = _state.value.copy(
                        allMechanics = mechanics,
                        mechanics = applyFilter(mechanics, _state.value.searchQuery),
                        assignedMechanicIds = assignedIds,
                        isLoading = false,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        error = e.message ?: "Failed to load mechanics",
                        isLoading = false,
                    )
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.value = _state.value.copy(
            searchQuery = query,
            mechanics = applyFilter(_state.value.allMechanics, query),
        )
    }

    fun assignMechanic(vehicleId: String, mechanicUserId: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(assigningMechanicId = mechanicUserId, assignError = null)
            assignmentRepository.createAssignment(vehicleId, mechanicUserId)
                .onSuccess {
                    _state.value = _state.value.copy(assigningMechanicId = null, assignSuccess = true)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        assigningMechanicId = null,
                        assignError = e.message ?: "Failed to assign mechanic",
                    )
                }
        }
    }

    fun clearAssignResult() {
        _state.value = _state.value.copy(assignSuccess = false, assignError = null)
    }

    private fun applyFilter(mechanics: List<MechanicProfile>, query: String): List<MechanicProfile> {
        if (query.isBlank()) return mechanics
        val q = query.trim().lowercase()
        return mechanics.filter { m ->
            m.shopName?.lowercase()?.contains(q) == true ||
            m.city?.lowercase()?.contains(q) == true ||
            m.state?.lowercase()?.contains(q) == true ||
            m.shopType?.lowercase()?.contains(q) == true ||
            m.bio?.lowercase()?.contains(q) == true
        }
    }
}
