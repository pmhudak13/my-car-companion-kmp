package org.mycarcompanion.app.ui.mechanics

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.repository.ProfileRepository

data class MechanicSetupState(
    val shopName: String = "",
    val shopType: String = "general",
    val bio: String = "",
    val city: String = "",
    val state: String = "",
    val yearsExperience: String = "",
    val hourlyRate: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isLoadingProfile: Boolean = false,
    val isPendingApproval: Boolean = false, // profile submitted, awaiting admin review
)

class MechanicSetupScreenModel(
    private val profileRepository: ProfileRepository,
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

    fun onShopNameChange(v: String) {
        _state.value = _state.value.copy(shopName = v, error = null)
    }

    fun onShopTypeChange(v: String) {
        _state.value = _state.value.copy(shopType = v)
    }

    fun onBioChange(v: String) {
        _state.value = _state.value.copy(bio = v)
    }

    fun onCityChange(v: String) {
        _state.value = _state.value.copy(city = v)
    }

    fun onStateChange(v: String) {
        _state.value = _state.value.copy(state = v)
    }

    fun onYearsChange(v: String) {
        _state.value = _state.value.copy(yearsExperience = v)
    }

    fun onRateChange(v: String) {
        _state.value = _state.value.copy(hourlyRate = v)
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
            profileRepository.upsertMechanicProfile(
                shopName = current.shopName,
                shopType = current.shopType,
                bio = current.bio.ifBlank { null },
                city = current.city.ifBlank { null },
                state = current.state.ifBlank { null },
                yearsExperience = years,
                hourlyRate = rate,
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
