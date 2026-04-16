package org.mycarcompanion.app.ui.subscription

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.repository.SubscriptionRepository

data class SubscribeState(
    val loading: Boolean = false,
    val checkoutUrl: String? = null,
    val error: String? = null,
)

class SubscribeScreenModel(
    private val subscriptionRepository: SubscriptionRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(SubscribeState())
    val state = _state.asStateFlow()

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

    fun clearCheckoutUrl() {
        _state.update { it.copy(checkoutUrl = null) }
    }
}
