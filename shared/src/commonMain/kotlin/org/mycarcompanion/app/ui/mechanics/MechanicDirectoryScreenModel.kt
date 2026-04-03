package org.mycarcompanion.app.ui.mechanics

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.MechanicProfile
import org.mycarcompanion.app.data.repository.MechanicRepository

data class MechanicDirectoryState(
    val mechanics: List<MechanicProfile> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class MechanicDirectoryScreenModel(
    private val mechanicRepository: MechanicRepository,
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
}
