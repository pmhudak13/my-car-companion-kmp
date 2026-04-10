package org.mycarcompanion.app.ui.reminders

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.mycarcompanion.app.data.models.Reminder
import org.mycarcompanion.app.data.models.Vehicle
import org.mycarcompanion.app.data.repository.ReminderRepository
import org.mycarcompanion.app.data.repository.VehicleRepository

data class RemindersListUiState(
    val reminders: List<Reminder> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val filter: String = "all",
    val deleteConfirmId: String? = null,
)

class RemindersListScreenModel(
    private val vehicleRepository: VehicleRepository,
    private val reminderRepository: ReminderRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(RemindersListUiState())
    val state: StateFlow<RemindersListUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        screenModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val vehiclesResult = vehicleRepository.getVehicles()
            if (vehiclesResult.isFailure) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = vehiclesResult.exceptionOrNull()?.message ?: "Failed to load vehicles",
                )
                return@launch
            }
            val vehicles = vehiclesResult.getOrDefault(emptyList())
            val vehicleIds = vehicles.map { it.id }
            val remindersResult = reminderRepository.getRemindersForVehicles(vehicleIds)
            _state.value = _state.value.copy(
                loading = false,
                vehicles = vehicles,
                reminders = remindersResult.getOrDefault(emptyList()),
                error = if (remindersResult.isFailure) remindersResult.exceptionOrNull()?.message else null,
            )
        }
    }

    fun setFilter(filter: String) {
        _state.value = _state.value.copy(filter = filter)
    }

    fun confirmDelete(id: String) {
        _state.value = _state.value.copy(deleteConfirmId = id)
    }

    fun cancelDelete() {
        _state.value = _state.value.copy(deleteConfirmId = null)
    }

    fun deleteReminder(id: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(deleteConfirmId = null)
            reminderRepository.deleteReminder(id)
            load()
        }
    }

    fun filteredReminders(): List<Reminder> {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString() // "YYYY-MM-DD"
        return when (_state.value.filter) {
            "active" -> _state.value.reminders.filter { it.isActive }
            "overdue" -> _state.value.reminders.filter { r ->
                r.nextDueDate != null && r.nextDueDate < today
            }
            else -> _state.value.reminders
        }
    }

    fun vehicleName(vehicleId: String): String {
        val v = _state.value.vehicles.find { it.id == vehicleId } ?: return "Unknown"
        return "${v.year} ${v.make} ${v.model}"
    }
}
