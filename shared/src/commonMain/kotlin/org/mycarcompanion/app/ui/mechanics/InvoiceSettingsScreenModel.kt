package org.mycarcompanion.app.ui.mechanics

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.InvoiceSettings
import org.mycarcompanion.app.data.repository.ProfileRepository

data class InvoiceSettingsState(
    val form: InvoiceSettings = InvoiceSettings(),
    val logoUrl: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isUploadingLogo: Boolean = false,
    val message: String? = null,
)

class InvoiceSettingsScreenModel(private val profileRepository: ProfileRepository) : ScreenModel {

    private val _state = MutableStateFlow(InvoiceSettingsState())
    val state: StateFlow<InvoiceSettingsState> = _state.asStateFlow()

    fun load() {
        screenModelScope.launch {
            val profile = profileRepository.getMyMechanicProfile().getOrNull()
            _state.value = _state.value.copy(
                isLoading = false,
                form = profile?.let {
                    InvoiceSettings(
                        phone = it.phone, address = it.address, city = it.city, state = it.state, zipCode = it.zipCode,
                        payZelle = it.payZelle, payVenmo = it.payVenmo, payCashapp = it.payCashapp,
                        payPaypal = it.payPaypal, payCardLink = it.payCardLink,
                        paymentNotes = it.paymentNotes, invoiceTerms = it.invoiceTerms,
                    )
                } ?: InvoiceSettings(),
                logoUrl = profile?.logoPath?.let { profileRepository.shopLogoUrl(it) },
            )
        }
    }

    fun update(form: InvoiceSettings) {
        _state.value = _state.value.copy(form = form, message = null)
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun save() {
        val f = _state.value.form
        // Store blanks as null and handles without the @/$ people tend to type
        val clean = f.copy(
            phone = f.phone.clean(), address = f.address.clean(), city = f.city.clean(),
            state = f.state.clean(), zipCode = f.zipCode.clean(),
            payZelle = f.payZelle.clean(),
            payVenmo = f.payVenmo.clean()?.removePrefix("@"),
            payCashapp = f.payCashapp.clean()?.removePrefix("$"),
            payPaypal = f.payPaypal.clean()?.removePrefix("paypal.me/"),
            payCardLink = f.payCardLink.clean(),
            paymentNotes = f.paymentNotes.clean(), invoiceTerms = f.invoiceTerms.clean(),
        )
        if (clean.payCardLink != null && !clean.payCardLink.startsWith("https://")) {
            _state.value = _state.value.copy(message = "The card payment link must start with https://")
            return
        }
        screenModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            profileRepository.updateInvoiceSettings(clean)
                .onSuccess { _state.value = _state.value.copy(isSaving = false, form = clean, message = "Saved") }
                .onFailure { e -> _state.value = _state.value.copy(isSaving = false, message = e.message ?: "Could not save") }
        }
    }

    fun uploadLogo(base64: String, mimeType: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(isUploadingLogo = true)
            profileRepository.uploadShopLogo(base64, mimeType)
                .onSuccess { path ->
                    _state.value = _state.value.copy(
                        isUploadingLogo = false,
                        logoUrl = profileRepository.shopLogoUrl(path),
                        message = "Logo updated",
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isUploadingLogo = false, message = e.message ?: "Logo upload failed")
                }
        }
    }

    private fun String?.clean(): String? = this?.trim()?.ifBlank { null }
}
