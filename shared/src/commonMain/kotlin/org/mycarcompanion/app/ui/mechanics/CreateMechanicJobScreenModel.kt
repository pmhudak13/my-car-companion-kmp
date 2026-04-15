package org.mycarcompanion.app.ui.mechanics

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.MechanicJob
import org.mycarcompanion.app.data.models.MechanicJobInsert
import org.mycarcompanion.app.data.repository.MechanicJobRepository

data class CreateJobForm(
    val clientName: String = "",
    val clientEmail: String = "",
    val vehicleMake: String = "",
    val vehicleModel: String = "",
    val vehicleYear: String = "",
    val vehicleVin: String = "",
    val vehicleColor: String = "",
    val vehicleLicensePlate: String = "",
    val description: String = "",
    val notes: String = "",
)

data class CreateMechanicJobState(
    val form: CreateJobForm = CreateJobForm(),
    val isSaving: Boolean = false,
    val error: String? = null,
    val createdJob: MechanicJob? = null,
)

class CreateMechanicJobScreenModel(
    private val jobRepository: MechanicJobRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(CreateMechanicJobState())
    val state: StateFlow<CreateMechanicJobState> = _state.asStateFlow()

    fun updateForm(form: CreateJobForm) {
        _state.value = _state.value.copy(form = form, error = null)
    }

    fun save() {
        val form = _state.value.form
        if (form.clientName.isBlank()) {
            _state.value = _state.value.copy(error = "Client name is required")
            return
        }
        if (form.vehicleMake.isBlank() || form.vehicleModel.isBlank()) {
            _state.value = _state.value.copy(error = "Vehicle make and model are required")
            return
        }
        val year = form.vehicleYear.toIntOrNull()
        if (year == null || year < 1900 || year > 2100) {
            _state.value = _state.value.copy(error = "Enter a valid vehicle year")
            return
        }

        screenModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            val insert = MechanicJobInsert(
                mechanicUserId = "",
                clientName = form.clientName.trim(),
                clientEmail = form.clientEmail.trim().ifBlank { null },
                vehicleMake = form.vehicleMake.trim(),
                vehicleModel = form.vehicleModel.trim(),
                vehicleYear = year,
                vehicleVin = form.vehicleVin.trim().ifBlank { null },
                vehicleColor = form.vehicleColor.trim().ifBlank { null },
                vehicleLicensePlate = form.vehicleLicensePlate.trim().ifBlank { null },
                description = form.description.trim().ifBlank { null },
                notes = form.notes.trim().ifBlank { null },
            )
            jobRepository.createJob(insert)
                .onSuccess { job ->
                    _state.value = _state.value.copy(isSaving = false, createdJob = job)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to create job",
                    )
                }
        }
    }
}
