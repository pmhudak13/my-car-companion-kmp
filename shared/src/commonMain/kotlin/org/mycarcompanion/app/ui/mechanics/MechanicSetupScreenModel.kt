package org.mycarcompanion.app.ui.mechanics

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.repository.ProfileRepository
import org.mycarcompanion.app.data.repository.StorageRepository

data class MechanicSetupState(
    val shopName: String = "",
    val shopType: String = "general",
    val bio: String = "",
    val city: String = "",
    val state: String = "",
    val yearsExperience: String = "",
    val hourlyRate: String = "",
    val googlePlaceUrl: String = "",
    val yelpUrl: String = "",
    val profileImageUrl: String? = null,
    val pendingPhotoBytes: ByteArray? = null,
    val isLoading: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isLoadingProfile: Boolean = false,
    val isPendingApproval: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MechanicSetupState) return false
        return shopName == other.shopName && shopType == other.shopType && bio == other.bio &&
            city == other.city && state == other.state && yearsExperience == other.yearsExperience &&
            hourlyRate == other.hourlyRate && googlePlaceUrl == other.googlePlaceUrl &&
            yelpUrl == other.yelpUrl && profileImageUrl == other.profileImageUrl &&
            (pendingPhotoBytes === other.pendingPhotoBytes ||
                pendingPhotoBytes?.contentEquals(other.pendingPhotoBytes ?: byteArrayOf()) == true) &&
            isLoading == other.isLoading && isUploadingPhoto == other.isUploadingPhoto &&
            error == other.error && isSaved == other.isSaved &&
            isLoadingProfile == other.isLoadingProfile && isPendingApproval == other.isPendingApproval
    }

    override fun hashCode(): Int {
        var result = shopName.hashCode()
        result = 31 * result + shopType.hashCode()
        result = 31 * result + bio.hashCode()
        result = 31 * result + city.hashCode()
        result = 31 * result + state.hashCode()
        result = 31 * result + yearsExperience.hashCode()
        result = 31 * result + hourlyRate.hashCode()
        result = 31 * result + googlePlaceUrl.hashCode()
        result = 31 * result + yelpUrl.hashCode()
        result = 31 * result + (profileImageUrl?.hashCode() ?: 0)
        result = 31 * result + (pendingPhotoBytes?.contentHashCode() ?: 0)
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + isUploadingPhoto.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + isSaved.hashCode()
        result = 31 * result + isLoadingProfile.hashCode()
        result = 31 * result + isPendingApproval.hashCode()
        return result
    }
}

class MechanicSetupScreenModel(
    private val profileRepository: ProfileRepository,
    private val storageRepository: StorageRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(MechanicSetupState())
    val state: StateFlow<MechanicSetupState> = _state.asStateFlow()

    init {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoadingProfile = true)
            profileRepository.getMyMechanicProfile()
                .onSuccess { profile ->
                    if (profile != null) {
                        _state.value = _state.value.copy(
                            shopName = profile.shopName ?: "",
                            shopType = profile.shopType,
                            bio = profile.bio ?: "",
                            city = profile.city ?: "",
                            state = profile.state ?: "",
                            yearsExperience = profile.yearsExperience?.toString() ?: "",
                            hourlyRate = profile.hourlyRate?.toString() ?: "",
                            googlePlaceUrl = profile.googlePlaceUrl ?: "",
                            yelpUrl = profile.yelpUrl ?: "",
                            profileImageUrl = profile.profileImageUrl,
                            isLoadingProfile = false,
                            isPendingApproval = profile.verificationStatus == "pending",
                        )
                    } else {
                        _state.value = _state.value.copy(isLoadingProfile = false)
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(isLoadingProfile = false)
                }
        }
    }

    fun onShopNameChange(v: String) { _state.value = _state.value.copy(shopName = v, error = null) }
    fun onShopTypeChange(v: String) { _state.value = _state.value.copy(shopType = v) }
    fun onBioChange(v: String) { _state.value = _state.value.copy(bio = v) }
    fun onCityChange(v: String) { _state.value = _state.value.copy(city = v) }
    fun onStateChange(v: String) { _state.value = _state.value.copy(state = v) }
    fun onYearsChange(v: String) { _state.value = _state.value.copy(yearsExperience = v) }
    fun onRateChange(v: String) { _state.value = _state.value.copy(hourlyRate = v) }
    fun onGooglePlaceUrlChange(v: String) { _state.value = _state.value.copy(googlePlaceUrl = v) }
    fun onYelpUrlChange(v: String) { _state.value = _state.value.copy(yelpUrl = v) }

    fun onPhotoPicked(bytes: ByteArray) {
        _state.value = _state.value.copy(pendingPhotoBytes = bytes)
    }

    fun save() {
        val current = _state.value
        if (current.shopName.isBlank()) {
            _state.value = current.copy(error = "Shop name is required")
            return
        }
        val years = if (current.yearsExperience.isBlank()) null else current.yearsExperience.toIntOrNull()
        val rate = if (current.hourlyRate.isBlank()) null else current.hourlyRate.toDoubleOrNull()

        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            // Upload photo if one was picked
            var uploadedImageUrl: String? = null
            val pending = _state.value.pendingPhotoBytes
            if (pending != null) {
                _state.value = _state.value.copy(isUploadingPhoto = true)
                storageRepository.uploadMechanicPhoto(pending)
                    .onSuccess { url -> uploadedImageUrl = url }
                    .onFailure { e ->
                        _state.value = _state.value.copy(
                            isLoading = false,
                            isUploadingPhoto = false,
                            error = "Photo upload failed: ${e.message}",
                        )
                        return@launch
                    }
                _state.value = _state.value.copy(isUploadingPhoto = false)
            }

            profileRepository.upsertMechanicProfile(
                shopName = current.shopName,
                shopType = current.shopType,
                bio = current.bio.ifBlank { null },
                city = current.city.ifBlank { null },
                state = current.state.ifBlank { null },
                yearsExperience = years,
                hourlyRate = rate,
                profileImageUrl = uploadedImageUrl,
                googlePlaceUrl = current.googlePlaceUrl.ifBlank { null },
                yelpUrl = current.yelpUrl.ifBlank { null },
            )
                .onSuccess {
                    _state.value = _state.value.copy(isLoading = false, isSaved = true)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to save profile",
                    )
                }
        }
    }
}
