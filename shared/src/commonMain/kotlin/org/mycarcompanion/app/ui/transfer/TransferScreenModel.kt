package org.mycarcompanion.app.ui.transfer

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.mycarcompanion.app.data.models.VehicleTransfer
import org.mycarcompanion.app.data.repository.TransferRepository
import kotlin.random.Random

data class TransferState(
    val loading: Boolean = true,
    val generating: Boolean = false,
    val activeTransfers: List<VehicleTransfer> = emptyList(),
    val error: String? = null,
)

class TransferScreenModel(
    private val transferRepository: TransferRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(TransferState())
    val state: StateFlow<TransferState> = _state.asStateFlow()

    fun load(vehicleId: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            transferRepository.getActiveTransfersForVehicle(vehicleId)
                .onSuccess { transfers ->
                    _state.value = _state.value.copy(loading = false, activeTransfers = transfers)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, error = e.message ?: "Failed to load")
                }
        }
    }

    fun generateCode(vehicleId: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(generating = true, error = null)
            val code = buildCode()
            val expiresAt = Clock.System.now().plus(48, DateTimeUnit.HOUR).toString()
            transferRepository.createTransfer(vehicleId, code, expiresAt)
                .onSuccess { transfer ->
                    _state.value = _state.value.copy(
                        generating = false,
                        activeTransfers = listOf(transfer) + _state.value.activeTransfers,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        generating = false,
                        error = e.message ?: "Failed to generate code",
                    )
                }
        }
    }

    private fun buildCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
