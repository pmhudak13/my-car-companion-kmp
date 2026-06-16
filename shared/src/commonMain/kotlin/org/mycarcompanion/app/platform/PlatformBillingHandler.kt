package org.mycarcompanion.app.platform

/** Describes a purchasable subscription offer from the platform's store. */
data class PlayProduct(
    val productId: String,
    val basePlanId: String,
    val displayPrice: String,
    val offerToken: String,
)

interface PlatformBillingHandler {
    /**
     * Platform-fixed: true on Android (Play Billing is the only permitted billing path),
     * false on web/iOS (use Stripe instead). Not tied to live BillingClient connection state —
     * gating the Stripe UI on connection state would violate Google Play policy.
     */
    val isAvailable: Boolean

    /** Queries the Play Store for subscription product details. Returns empty list on non-Android. */
    suspend fun queryProducts(productIds: List<String>): List<PlayProduct>

    /**
     * Launches the Play Billing purchase flow for the given [productId] + [basePlanId].
     * Returns the purchase token on success (used for server-side verification).
     */
    suspend fun purchase(productId: String, basePlanId: String): Result<String>
}

/** Used on web and iOS where Play Billing is not available. */
object NoOpBillingHandler : PlatformBillingHandler {
    override val isAvailable = false
    override suspend fun queryProducts(productIds: List<String>) = emptyList<PlayProduct>()
    override suspend fun purchase(productId: String, basePlanId: String) =
        Result.failure<String>(UnsupportedOperationException("Play Billing not available on this platform"))
}
