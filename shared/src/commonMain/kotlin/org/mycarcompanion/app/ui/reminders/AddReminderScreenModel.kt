package org.mycarcompanion.app.ui.reminders

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.Reminder
import org.mycarcompanion.app.data.models.ReminderFormData
import org.mycarcompanion.app.data.repository.ReminderRepository

data class AddReminderState(
    val form: ReminderFormData = ReminderFormData(),
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class AddReminderScreenModel(
    private val reminderRepository: ReminderRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(AddReminderState())
    val state: StateFlow<AddReminderState> = _state.asStateFlow()

    fun updateForm(form: ReminderFormData) {
        _state.value = _state.value.copy(form = form)
    }

    fun save(vehicleId: String) {
        val form = _state.value.form
        if (form.type.isBlank()) {
            _state.value = _state.value.copy(error = "Reminder type is required")
            return
        }
        if (form.type == "custom" && form.customName.isBlank()) {
            _state.value = _state.value.copy(error = "Custom name is required for custom reminders")
            return
        }
        if (form.nextDueDate.isBlank() && form.nextDueMileage.isBlank()) {
            _state.value = _state.value.copy(error = "At least a due date or due mileage is required")
            return
        }
        if (form.nextDueDate.isNotBlank()) {
            val dateParts = form.nextDueDate.trim().split("-")
            val valid = form.nextDueDate.trim().length == 10 &&
                dateParts.size == 3 &&
                dateParts[0].toIntOrNull()?.let { it in 1900..2100 } == true &&
                dateParts[1].toIntOrNull()?.let { it in 1..12 } == true &&
                dateParts[2].toIntOrNull()?.let { it in 1..31 } == true
            if (!valid) {
                _state.value = _state.value.copy(error = "Date must be in YYYY-MM-DD format")
                return
            }
        }

        screenModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            val reminder = Reminder(
                vehicleId = vehicleId,
                type = form.type,
                customName = form.customName.trim().ifBlank { null },
                nextDueDate = form.nextDueDate.trim().ifBlank { null },
                nextDueMileage = form.nextDueMileage.toIntOrNull(),
                intervalMonths = form.intervalMonths.toIntOrNull(),
                intervalMiles = form.intervalMiles.toIntOrNull(),
            )
            reminderRepository.addReminder(reminder)
                .onSuccess { _state.value = _state.value.copy(isSaving = false, saved = true) }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to save",
                    )
                }
        }
    }
}
