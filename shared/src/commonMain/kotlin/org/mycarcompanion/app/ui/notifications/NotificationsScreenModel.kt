package org.mycarcompanion.app.ui.notifications

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.NotificationPreferences
import org.mycarcompanion.app.data.repository.NotificationPreferencesRepository

data class NotificationsUiState(
    val oilChange: Boolean = true,
    val tireRotation: Boolean = true,
    val registration: Boolean = true,
    val customReminders: Boolean = true,
    val newMessages: Boolean = true,
    val mechanicUpdates: Boolean = true,
    val loading: Boolean = true,
)

class NotificationsScreenModel(
    private val repository: NotificationPreferencesRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        screenModelScope.launch {
            repository.getPreferences().onSuccess { prefs ->
                _state.value = NotificationsUiState(
                    oilChange = prefs.oilChange,
                    tireRotation = prefs.tireRotation,
                    registration = prefs.registration,
                    customReminders = prefs.customReminders,
                    newMessages = prefs.newMessages,
                    mechanicUpdates = prefs.mechanicUpdates,
                    loading = false,
                )
            }.onFailure {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    fun setOilChange(value: Boolean) = update { it.copy(oilChange = value) }
    fun setTireRotation(value: Boolean) = update { it.copy(tireRotation = value) }
    fun setRegistration(value: Boolean) = update { it.copy(registration = value) }
    fun setCustomReminders(value: Boolean) = update { it.copy(customReminders = value) }
    fun setNewMessages(value: Boolean) = update { it.copy(newMessages = value) }
    fun setMechanicUpdates(value: Boolean) = update { it.copy(mechanicUpdates = value) }

    private fun update(transform: (NotificationsUiState) -> NotificationsUiState) {
        _state.value = transform(_state.value)
        save()
    }

    private fun save() {
        val s = _state.value
        screenModelScope.launch {
            repository.savePreferences(
                NotificationPreferences(
                    oilChange = s.oilChange,
                    tireRotation = s.tireRotation,
                    registration = s.registration,
                    customReminders = s.customReminders,
                    newMessages = s.newMessages,
                    mechanicUpdates = s.mechanicUpdates,
                )
            )
        }
    }
}
