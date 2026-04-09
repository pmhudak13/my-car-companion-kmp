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
    val mechanics: List<MechanicProfile> = emptyList(),
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
        loadMechanics()
    }

    fun loadMechanics() {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            mechanicRepository.getVerifiedMechanics()
                .onSuccess { mechanics ->
                    _state.value = _state.value.copy(mechanics = mechanics, isLoading = false)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        error = e.message ?: "Failed to load mechanics",
                        isLoading = false,
                    )
                }
        }
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
}
