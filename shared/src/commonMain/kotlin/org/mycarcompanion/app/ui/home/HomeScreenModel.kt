package org.mycarcompanion.app.ui.home

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.AuthResult
import org.mycarcompanion.app.data.models.AuthState
import org.mycarcompanion.app.data.models.Reminder
import org.mycarcompanion.app.data.models.Vehicle
import org.mycarcompanion.app.data.repository.AuthRepository
import org.mycarcompanion.app.data.repository.MessageRepository
import org.mycarcompanion.app.data.repository.ReminderRepository
import org.mycarcompanion.app.data.repository.VehicleRepository

data class VehicleUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val upcomingReminders: List<Reminder> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

class HomeScreenModel(
    private val authRepository: AuthRepository,
    private val vehicleRepository: VehicleRepository,
    private val messageRepository: MessageRepository,
    private val reminderRepository: ReminderRepository,
) : ScreenModel {

    val authState = authRepository.authState.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthState.Loading,
    )

    private val _vehicleState = MutableStateFlow(VehicleUiState())
    val vehicleState: StateFlow<VehicleUiState> = _vehicleState.asStateFlow()

    private val _linkState = MutableStateFlow<AuthResult?>(null)
    val linkState: StateFlow<AuthResult?> = _linkState.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    // Called from Content() via LaunchedEffect(Unit), so it re-runs every time the
    // screen returns to the front of the stack. When data is already loaded the
    // refresh is silent: stale data stays visible while the new list loads.
    fun refresh(fromPull: Boolean = false) {
        screenModelScope.launch {
            // Best-effort unread badge; failures just leave the previous count
            messageRepository.getInbox().onSuccess { inbox ->
                _unreadCount.value = inbox.count { !it.isRead }
            }
        }
        screenModelScope.launch {
            val silent = _vehicleState.value.vehicles.isNotEmpty()
            if (fromPull) {
                _vehicleState.value = _vehicleState.value.copy(isRefreshing = true)
            } else if (!silent) {
                _vehicleState.value = _vehicleState.value.copy(isLoading = true, error = null)
            }
            vehicleRepository.getVehicles()
                .onSuccess { vehicles ->
                    _vehicleState.value = _vehicleState.value.copy(
                        vehicles = vehicles,
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                    )
                    loadUpcomingReminders(vehicles)
                }
                .onFailure { e ->
                    // On a silent refresh keep showing the stale list instead of an error
                    _vehicleState.value = if (silent) {
                        _vehicleState.value.copy(isRefreshing = false)
                    } else {
                        _vehicleState.value.copy(
                            error = e.message ?: "Failed to load vehicles",
                            isLoading = false,
                            isRefreshing = false,
                        )
                    }
                }
        }
    }

    // Soonest-due active reminders across all vehicles, dated ones first
    private fun loadUpcomingReminders(vehicles: List<Vehicle>) {
        screenModelScope.launch {
            if (vehicles.isEmpty()) {
                _vehicleState.value = _vehicleState.value.copy(upcomingReminders = emptyList())
                return@launch
            }
            reminderRepository.getRemindersForVehicles(vehicles.map { it.id })
                .onSuccess { reminders ->
                    val upcoming = reminders
                        .filter { it.isActive && (it.nextDueDate != null || it.nextDueMileage != null) }
                        .sortedWith(compareBy(nullsLast(naturalOrder()), Reminder::nextDueDate))
                        .take(3)
                    _vehicleState.value = _vehicleState.value.copy(upcomingReminders = upcoming)
                }
        }
    }

    fun linkGoogleAccount() {
        screenModelScope.launch {
            _linkState.value = authRepository.linkGoogleIdentity()
        }
    }

    fun clearLinkState() {
        _linkState.value = null
    }

    fun signOut() {
        screenModelScope.launch {
            authRepository.signOut()
        }
    }
}
