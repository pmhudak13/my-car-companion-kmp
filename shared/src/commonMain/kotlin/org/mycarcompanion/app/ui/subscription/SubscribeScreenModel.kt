package org.mycarcompanion.app.ui.subscription

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.repository.ProfileRepository
import org.mycarcompanion.app.data.repository.SubscriptionRepository

data class SubscribeState(
    val loading: Boolean = false,
    val checkoutUrl: String? = null,
    val portalUrl: String? = null,
    val error: String? = null,
    val isPremium: Boolean = false,
    val subscriptionTier: String = "free",
    // True after checkout URL is opened — prompts user to refresh once they've paid
    val checkoutInitiated: Boolean = false,
)

class SubscribeScreenModel(
    private val subscriptionRepository: SubscriptionRepository,
    private val profileRepository: ProfileRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(SubscribeState())
    val state = _state.asStateFlow()

    init {
        screenModelScope.launch {
            profileRepository.getMyProfile().onSuccess { profile ->
                _state.update {
                    it.copy(
                        isPremium = profile?.isPremium == true,
                        subscriptionTier = profile?.subscriptionTier ?: "free",
                    )
                }
            }
        }
    }

    fun startCheckout(priceId: String) {
        screenModelScope.launch {
            _state.update { it.copy(loading = true, error = null, checkoutUrl = null) }
            subscriptionRepository.createCheckoutSession(priceId)
                .onSuccess { url ->
                    _state.update { it.copy(loading = false, checkoutUrl = url) }
                }
                .onFailure { err ->
                    _state.update { it.copy(loading = false, error = err.message ?: "Something went wrong") }
                }
        }
    }

    fun openPortal() {
        screenModelScope.launch {
            _state.update { it.copy(loading = true, error = null, portalUrl = null) }
            subscriptionRepository.createPortalSession()
                .onSuccess { url ->
                    _state.update { it.copy(loading = false, portalUrl = url) }
                }
                .onFailure { err ->
                    _state.update { it.copy(loading = false, error = err.message ?: "Failed to open subscription portal") }
                }
        }
    }

    /** Re-fetches the profile — call after returning from Stripe to pick up premium status. */
    fun refreshProfile() {
        screenModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            profileRepository.getMyProfile()
                .onSuccess { profile ->
                    _state.update {
                        it.copy(
                            loading = false,
                            isPremium = profile?.isPremium == true,
                            subscriptionTier = profile?.subscriptionTier ?: "free",
                            checkoutInitiated = false,
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(loading = false) }
                }
        }
    }

    fun clearCheckoutUrl() {
        // Mark that checkout was initiated so the UI can show a "check status" prompt
        _state.update { it.copy(checkoutUrl = null, checkoutInitiated = true) }
    }

    fun clearPortalUrl() {
        _state.update { it.copy(portalUrl = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
