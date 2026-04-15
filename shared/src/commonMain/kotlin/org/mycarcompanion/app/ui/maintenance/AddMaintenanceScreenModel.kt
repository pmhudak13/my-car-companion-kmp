package org.mycarcompanion.app.ui.maintenance

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.mycarcompanion.app.data.models.MaintenanceFormData
import org.mycarcompanion.app.data.models.MaintenanceLog
import org.mycarcompanion.app.data.repository.MaintenanceRepository
import org.mycarcompanion.app.data.repository.StorageRepository

data class AddMaintenanceState(
    val form: MaintenanceFormData = MaintenanceFormData(),
    val isSaving: Boolean = false,
    val isUploadingPhotos: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class AddMaintenanceScreenModel(
    private val maintenanceRepository: MaintenanceRepository,
    private val storageRepository: StorageRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(AddMaintenanceState())
    val state: StateFlow<AddMaintenanceState> = _state.asStateFlow()

    fun updateForm(form: MaintenanceFormData) {
        _state.value = _state.value.copy(form = form)
    }

    fun addPhoto(bytes: ByteArray) {
        val updated = _state.value.form.photoUris + bytes
        _state.value = _state.value.copy(form = _state.value.form.copy(photoUris = updated))
    }

    fun removePhoto(index: Int) {
        val updated = _state.value.form.photoUris.toMutableList().also { it.removeAt(index) }
        _state.value = _state.value.copy(form = _state.value.form.copy(photoUris = updated))
    }

    fun save(vehicleId: String, requiresApproval: Boolean = false) {
        val form = _state.value.form
        if (form.category.isBlank() || form.description.isBlank() || form.date.isBlank()) {
            _state.value = _state.value.copy(error = "Category, description, and date are required")
            return
        }
        val mileageInt = form.mileage.toIntOrNull()
        if (mileageInt == null) {
            _state.value = _state.value.copy(error = "Mileage must be a number")
            return
        }

        screenModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)

            // Upload photos concurrently
            val uploadedUrls = mutableListOf<String>()
            if (form.photoUris.isNotEmpty()) {
                _state.value = _state.value.copy(isUploadingPhotos = true)
                val tempLogId = "tmp_${Clock.System.now().toEpochMilliseconds()}"
                val results = form.photoUris.mapIndexed { i, bytes ->
                    async {
                        storageRepository.uploadMaintenancePhoto(tempLogId, i, bytes)
                    }
                }.awaitAll()

                val failed = results.firstOrNull { it.isFailure }
                if (failed != null) {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        isUploadingPhotos = false,
                        error = "Photo upload failed: ${failed.exceptionOrNull()?.message}",
                    )
                    return@launch
                }
                uploadedUrls.addAll(results.mapNotNull { it.getOrNull() })
                _state.value = _state.value.copy(isUploadingPhotos = false)
            }

            val log = MaintenanceLog(
                vehicleId = vehicleId,
                category = form.category,
                description = form.description.trim(),
                date = form.date.trim(),
                mileage = mileageInt,
                cost = form.cost.toDoubleOrNull(),
                notes = form.notes.trim().ifBlank { null },
                photoUrls = uploadedUrls.ifEmpty { null },
                approvalStatus = if (requiresApproval) "pending" else null,
            )
            maintenanceRepository.addLog(log)
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
