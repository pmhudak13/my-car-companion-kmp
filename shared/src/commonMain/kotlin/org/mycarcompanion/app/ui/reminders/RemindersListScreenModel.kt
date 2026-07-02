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

    // Re-triggered from Content() on every return; silent once data is loaded
    // (also keeps deletes from flashing a full-screen spinner).
    fun refresh() {
        screenModelScope.launch {
            val silent = _state.value.vehicles.isNotEmpty()
            if (!silent) {
                _state.value = _state.value.copy(loading = true, error = null)
            }
            val vehiclesResult = vehicleRepository.getVehicles()
            if (vehiclesResult.isFailure) {
                if (!silent) {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = vehiclesResult.exceptionOrNull()?.message ?: "Failed to load vehicles",
                    )
                }
                return@launch
            }
            val vehicles = vehiclesResult.getOrDefault(emptyList())
            val vehicleIds = vehicles.map { it.id }
            val remindersResult = reminderRepository.getRemindersForVehicles(vehicleIds)
            _state.value = _state.value.copy(
                loading = false,
                vehicles = vehicles,
                // Keep the stale list if a silent refresh fails
                reminders = remindersResult.getOrElse { _state.value.reminders },
                error = if (remindersResult.isFailure && !silent) remindersResult.exceptionOrNull()?.message else null,
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
            refresh()
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
