package org.mycarcompanion.app.ui.subscription

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.repository.ProfileRepository
import org.mycarcompanion.app.data.repository.SubscriptionRepository
import org.mycarcompanion.app.platform.PlayProduct
import org.mycarcompanion.app.platform.PlatformBillingHandler

data class SubscribeState(
    // Start loading=true so we never briefly render Stripe UI before the billing check completes.
    val loading: Boolean = true,
    val checkoutUrl: String? = null,
    val portalUrl: String? = null,
    val error: String? = null,
    val isPremium: Boolean = false,
    val subscriptionTier: String = "free",
    // True after checkout URL is opened — prompts user to refresh once they've paid
    val checkoutInitiated: Boolean = false,
    // Play Billing
    val playBillingAvailable: Boolean = false,
    val playProducts: List<PlayProduct> = emptyList(),
    val playBillingLoading: Boolean = false,
)

class SubscribeScreenModel(
    private val subscriptionRepository: SubscriptionRepository,
    private val profileRepository: ProfileRepository,
    private val billingHandler: PlatformBillingHandler,
) : ScreenModel {

    private val _state = MutableStateFlow(SubscribeState())
    val state = _state.asStateFlow()

    init {
        // playBillingAvailable is platform-fixed (true on Android, false on web/iOS), NOT derived
        // from whether the Play product query succeeded. Deriving it from products.isNotEmpty()
        // meant a failed/empty Play query on Android fell back to the Stripe UI — a Google Play
        // policy violation. Stripe must only ever appear on web/iOS.
        _state.update { it.copy(playBillingAvailable = billingHandler.isAvailable) }

        // Load profile and query billing in parallel. Only clear the loading spinner once both
        // complete so the UI never briefly shows the wrong billing path while data loads.
        screenModelScope.launch {
            coroutineScope {
                val profileJob = async {
                    profileRepository.getMyProfile().onSuccess { profile ->
                        _state.update {
                            it.copy(
                                isPremium = profile?.isPremium == true,
                                subscriptionTier = profile?.subscriptionTier ?: "free",
                            )
                        }
                    }
                }
                val billingJob = async {
                    if (billingHandler.isAvailable) {
                        val products = billingHandler.queryProducts(listOf("premium", "mechanic_pro"))
                        _state.update { it.copy(playProducts = products) }
                    }
                }
                profileJob.await()
                billingJob.await()
            }
            _state.update { it.copy(loading = false) }
        }
    }

    /** Initiates a Play Billing purchase for the given product + base plan. */
    fun startPlayPurchase(productId: String, basePlanId: String) {
        screenModelScope.launch {
            _state.update { it.copy(playBillingLoading = true, error = null) }
            billingHandler.purchase(productId, basePlanId)
                .onSuccess { purchaseToken ->
                    // Acknowledge + verify server-side
                    subscriptionRepository.verifyPlayPurchase(purchaseToken, productId)
                        .onSuccess {
                            // Reload profile to pick up new subscription tier
                            profileRepository.getMyProfile().onSuccess { profile ->
                                _state.update {
                                    it.copy(
                                        playBillingLoading = false,
                                        isPremium = profile?.isPremium == true,
                                        subscriptionTier = profile?.subscriptionTier ?: "free",
                                    )
                                }
                            }
                        }
                        .onFailure { err ->
                            _state.update {
                                it.copy(
                                    playBillingLoading = false,
                                    error = "Purchase received but verification failed: ${err.message}",
                                )
                            }
                        }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            playBillingLoading = false,
                            error = if (err.message == "Purchase cancelled") null else err.message ?: "Purchase failed",
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
        _state.update { it.copy(checkoutUrl = null, checkoutInitiated = true) }
    }

    fun clearPortalUrl() {
        _state.update { it.copy(portalUrl = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
