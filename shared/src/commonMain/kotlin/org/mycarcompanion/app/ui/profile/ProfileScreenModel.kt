package org.mycarcompanion.app.ui.profile

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.repository.ProfileRepository
import org.mycarcompanion.app.data.repository.StorageRepository

data class ProfileUiState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val pendingAvatarBytes: ByteArray? = null, // picked but not yet uploaded
    val loading: Boolean = true,
    val saving: Boolean = false,
    val uploadingPhoto: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
) {
    // ByteArray equality is identity by default; override so state changes propagate
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProfileUiState) return false
        return firstName == other.firstName &&
            lastName == other.lastName &&
            email == other.email &&
            avatarUrl == other.avatarUrl &&
            (pendingAvatarBytes === other.pendingAvatarBytes || pendingAvatarBytes?.contentEquals(other.pendingAvatarBytes ?: byteArrayOf()) == true) &&
            loading == other.loading &&
            saving == other.saving &&
            uploadingPhoto == other.uploadingPhoto &&
            error == other.error &&
            saved == other.saved
    }

    override fun hashCode(): Int {
        var result = firstName.hashCode()
        result = 31 * result + lastName.hashCode()
        result = 31 * result + email.hashCode()
        result = 31 * result + (avatarUrl?.hashCode() ?: 0)
        result = 31 * result + (pendingAvatarBytes?.contentHashCode() ?: 0)
        result = 31 * result + loading.hashCode()
        result = 31 * result + saving.hashCode()
        result = 31 * result + uploadingPhoto.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + saved.hashCode()
        return result
    }
}

class ProfileScreenModel(
    private val profileRepository: ProfileRepository,
    private val storageRepository: StorageRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        screenModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val result = profileRepository.getMyProfile()
            val profile = result.getOrNull()
            _state.value = _state.value.copy(
                loading = false,
                firstName = profile?.firstName ?: "",
                lastName = profile?.lastName ?: "",
                email = profile?.email ?: "",
                avatarUrl = profile?.avatarUrl,
                error = if (result.isFailure) result.exceptionOrNull()?.message else null,
            )
        }
    }

    fun updateFirstName(value: String) {
        _state.value = _state.value.copy(firstName = value, saved = false)
    }

    fun updateLastName(value: String) {
        _state.value = _state.value.copy(lastName = value, saved = false)
    }

    fun onAvatarPicked(bytes: ByteArray) {
        _state.value = _state.value.copy(pendingAvatarBytes = bytes)
    }

    fun save() {
        screenModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)

            // Upload avatar first if one was picked
            var uploadedUrl: String? = null
            val pending = _state.value.pendingAvatarBytes
            if (pending != null) {
                _state.value = _state.value.copy(uploadingPhoto = true)
                storageRepository.uploadAvatar(pending)
                    .onSuccess { url -> uploadedUrl = url }
                    .onFailure { e ->
                        _state.value = _state.value.copy(
                            saving = false,
                            uploadingPhoto = false,
                            error = "Photo upload failed: ${e.message}",
                        )
                        return@launch
                    }
                _state.value = _state.value.copy(uploadingPhoto = false)
            }

            val result = profileRepository.updateProfile(
                firstName = _state.value.firstName,
                lastName = _state.value.lastName,
                avatarUrl = uploadedUrl,
            )
            if (result.isSuccess) {
                _state.value = _state.value.copy(
                    saving = false,
                    saved = true,
                    avatarUrl = uploadedUrl ?: _state.value.avatarUrl,
                    pendingAvatarBytes = null,
                )
            } else {
                _state.value = _state.value.copy(
                    saving = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to save profile",
                )
            }
        }
    }
}
