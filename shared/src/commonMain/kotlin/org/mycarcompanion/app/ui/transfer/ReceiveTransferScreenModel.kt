package org.mycarcompanion.app.ui.transfer

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.VehicleTransfer
import org.mycarcompanion.app.data.repository.TransferRepository
import org.mycarcompanion.app.data.repository.VehicleRepository

data class ReceiveTransferState(
    val code: String = "",
    val looking: Boolean = false,
    val transfer: VehicleTransfer? = null,
    val vehicleLabel: String? = null,
    val claiming: Boolean = false,
    val claimed: Boolean = false,
    val lookupError: String? = null,
    val claimError: String? = null,
)

class ReceiveTransferScreenModel(
    private val transferRepository: TransferRepository,
    private val vehicleRepository: VehicleRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(ReceiveTransferState())
    val state: StateFlow<ReceiveTransferState> = _state.asStateFlow()

    fun setCode(code: String) {
        _state.value = _state.value.copy(
            code = code.uppercase(),
            transfer = null,
            vehicleLabel = null,
            lookupError = null,
            claimError = null,
        )
    }

    fun lookupCode() {
        val code = _state.value.code.trim()
        if (code.isEmpty()) return
        screenModelScope.launch {
            _state.value = _state.value.copy(looking = true, lookupError = null, transfer = null, vehicleLabel = null)
            val transfer = transferRepository.lookupByCode(code)
                .getOrElse { e ->
                    _state.value = _state.value.copy(looking = false, lookupError = e.message ?: "Lookup failed")
                    return@launch
                }
            if (transfer == null) {
                _state.value = _state.value.copy(looking = false, lookupError = "Code not found or expired")
                return@launch
            }
            val vehicle = vehicleRepository.getVehicle(transfer.vehicleId).getOrNull()
            val label = if (vehicle != null) "${vehicle.year} ${vehicle.make} ${vehicle.model}" else "Unknown Vehicle"
            _state.value = _state.value.copy(looking = false, transfer = transfer, vehicleLabel = label)
        }
    }

    fun claimTransfer() {
        val transfer = _state.value.transfer ?: return
        screenModelScope.launch {
            _state.value = _state.value.copy(claiming = true, claimError = null)
            transferRepository.claimTransfer(transfer)
                .onSuccess {
                    _state.value = _state.value.copy(claiming = false, claimed = true)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(claiming = false, claimError = e.message ?: "Failed to claim vehicle")
                }
        }
    }
}
