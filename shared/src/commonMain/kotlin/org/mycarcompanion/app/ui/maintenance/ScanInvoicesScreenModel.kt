package org.mycarcompanion.app.ui.maintenance

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.ImportedRecord
import org.mycarcompanion.app.data.models.MaintenanceLog
import org.mycarcompanion.app.data.models.Vehicle
import org.mycarcompanion.app.data.repository.MaintenanceRepository
import org.mycarcompanion.app.data.repository.VehicleRepository

data class ScanInvoicesState(
    val vehicle: Vehicle? = null,
    val isScanning: Boolean = false,
    val isSaving: Boolean = false,
    val records: List<ImportedRecord> = emptyList(),
    /** Index-aligned with [records]: which ones the owner keeps. */
    val keep: List<Boolean> = emptyList(),
    val scanId: String? = null,
    val foundVin: String? = null,
    val updateOdometer: Boolean = true,
    val fillVin: Boolean = true,
    val error: String? = null,
    val savedCount: Int? = null,
) {
    private val kept get() = records.filterIndexed { i, r -> keep.getOrElse(i) { false } && r.isValid }
    val keptCount: Int get() = kept.size
    /** Highest mileage on the kept records, only when it's above the car's odometer (it never goes down). */
    val newOdometer: Int? get() = kept.maxOfOrNull { it.mileage }?.takeIf { it > (vehicle?.odometer ?: 0) }
    /** Offered only when the car has no VIN yet. */
    val vinToFill: String? get() = foundVin?.takeIf { vehicle?.vin.isNullOrBlank() }
    fun keptRecords() = kept
}

class ScanInvoicesScreenModel(
    private val maintenanceRepository: MaintenanceRepository,
    private val vehicleRepository: VehicleRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(ScanInvoicesState())
    val state: StateFlow<ScanInvoicesState> = _state.asStateFlow()

    fun load(vehicleId: String) {
        if (_state.value.vehicle != null) return
        screenModelScope.launch {
            vehicleRepository.getVehicle(vehicleId).onSuccess { v -> _state.update { it.copy(vehicle = v) } }
        }
    }

    fun scan(vehicleId: String, fileName: String, base64: String, mimeType: String) {
        _state.update { it.copy(isScanning = true, error = null, savedCount = null) }
        screenModelScope.launch {
            maintenanceRepository.scanInvoice(vehicleId, base64, mimeType, fileName)
                .onSuccess { r ->
                    _state.update {
                        if (r.records.isEmpty()) {
                            it.copy(isScanning = false, error = "No service records found. Try a clearer photo or the full PDF.")
                        } else {
                            it.copy(
                                isScanning = false, records = r.records, keep = r.records.map { rec -> rec.isValid },
                                scanId = r.scanId, foundVin = r.vin,
                            )
                        }
                    }
                }
                .onFailure { e -> _state.update { it.copy(isScanning = false, error = e.message ?: "Could not scan that file") } }
        }
    }

    fun toggle(index: Int) = _state.update { s ->
        s.copy(keep = s.keep.mapIndexed { i, k -> if (i == index) !k else k })
    }

    fun setUpdateOdometer(on: Boolean) = _state.update { it.copy(updateOdometer = on) }
    fun setFillVin(on: Boolean) = _state.update { it.copy(fillVin = on) }

    fun save(vehicleId: String) {
        val s = _state.value
        val kept = s.keptRecords()
        if (kept.isEmpty()) return
        _state.update { it.copy(isSaving = true, error = null) }
        screenModelScope.launch {
            val logs = kept.map {
                MaintenanceLog(
                    vehicleId = vehicleId,
                    source = "self",
                    category = it.category,
                    description = it.description,
                    date = it.date,
                    mileage = it.mileage,
                    cost = it.cost,
                    notes = it.notes,
                    invoiceScanId = s.scanId,
                )
            }
            maintenanceRepository.addLogs(logs)
                .onSuccess { count ->
                    // car updates are extras: the records are saved even if one of these fails
                    s.newOdometer?.takeIf { s.updateOdometer }?.let { vehicleRepository.updateOdometer(vehicleId, it) }
                    s.vinToFill?.takeIf { s.fillVin }?.let { vehicleRepository.fillVinIfBlank(vehicleId, it) }
                    _state.update { ScanInvoicesState(vehicle = it.vehicle, savedCount = count) }
                    // reload so the odometer/VIN shown on the next scan are current
                    vehicleRepository.getVehicle(vehicleId).onSuccess { v -> _state.update { st -> st.copy(vehicle = v) } }
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message ?: "Could not save the records") } }
        }
    }
}
