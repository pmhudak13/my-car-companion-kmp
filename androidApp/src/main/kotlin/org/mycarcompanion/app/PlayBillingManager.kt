package org.mycarcompanion.app

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.mycarcompanion.app.platform.PlayProduct
import org.mycarcompanion.app.platform.PlatformBillingHandler
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/**
 * Play Billing Manager — wraps BillingClient 9.x.
 *
 * Product setup in Play Console (Subscriptions tab):
 *   Product ID: "premium"      → base plans: "monthly" ($4.99/mo), "yearly" ($49.99/yr)
 *   Product ID: "mechanic_pro" → base plans: "monthly" ($14.99/mo), "yearly" ($149.99/yr)
 *
 * After creating products in Play Console, activate the base plans, then rebuild and test
 * with a licensed tester account before publishing.
 */
class PlayBillingManager(context: Context) : PlatformBillingHandler {

    companion object {
        val PRODUCT_IDS = listOf("premium", "mechanic_pro")
    }

    private var activityRef = WeakReference<Activity>(null)
    private var pendingPurchase: CompletableDeferred<Result<String>>? = null
    private val cachedProductDetails = mutableMapOf<String, ProductDetails>()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        val deferred = pendingPurchase ?: return@PurchasesUpdatedListener
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchase = purchases?.firstOrNull()
                if (purchase != null) {
                    deferred.complete(Result.success(purchase.purchaseToken))
                } else {
                    deferred.complete(Result.failure(Exception("No purchase returned")))
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                deferred.complete(Result.failure(Exception("Purchase cancelled")))
            else ->
                deferred.complete(Result.failure(Exception("Billing error: ${billingResult.debugMessage}")))
        }
        pendingPurchase = null
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    // Platform-fixed: on Android, Play Billing is the only permitted billing method, so this is
    // always true regardless of live BillingClient connection state. Gating it on isReady would
    // let the Stripe UI show before/while BillingClient connects — a Google Play policy violation.
    // Connection readiness is handled where it matters (queryProducts/purchase call connect()).
    override val isAvailable: Boolean = true

    /** Called by MainActivity.onResume / onPause to keep the Activity reference current. */
    fun setActivity(activity: Activity?) {
        activityRef = WeakReference(activity)
    }

    /** Connects the BillingClient if not already connected. */
    suspend fun connect(): Boolean = suspendCancellableCoroutine { cont ->
        if (billingClient.isReady) {
            cont.resume(true)
            return@suspendCancellableCoroutine
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
            }
            override fun onBillingServiceDisconnected() {
                if (cont.isActive) cont.resume(false)
            }
        })
    }

    override suspend fun queryProducts(productIds: List<String>): List<PlayProduct> =
        withContext(Dispatchers.IO) {
            if (!connect()) return@withContext emptyList()

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    productIds.map { id ->
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(id)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    }
                )
                .build()

            val result = mutableListOf<PlayProduct>()
            suspendCancellableCoroutine { cont ->
                billingClient.queryProductDetailsAsync(params) { billingResult, details ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        for (detail in details.productDetailsList) {
                            cachedProductDetails[detail.productId] = detail
                            detail.subscriptionOfferDetails?.forEach { offer ->
                                val pricingPhase = offer.pricingPhases.pricingPhaseList
                                    .lastOrNull { it.billingPeriod.isNotEmpty() }
                                result.add(
                                    PlayProduct(
                                        productId = detail.productId,
                                        basePlanId = offer.basePlanId,
                                        displayPrice = pricingPhase?.formattedPrice ?: "",
                                        offerToken = offer.offerToken,
                                    )
                                )
                            }
                        }
                    }
                    cont.resume(Unit)
                }
            }
            result
        }

    override suspend fun purchase(productId: String, basePlanId: String): Result<String> {
        val activity = activityRef.get()
            ?: return Result.failure(Exception("Activity not available — please try again"))

        if (!connect()) return Result.failure(Exception("Could not connect to Google Play"))

        val productDetails = cachedProductDetails[productId]
            ?: run {
                // Re-query if cache is cold
                queryProducts(listOf(productId))
                cachedProductDetails[productId]
            }
            ?: return Result.failure(Exception("Product not found: $productId"))

        val offerToken = productDetails.subscriptionOfferDetails
            ?.firstOrNull { it.basePlanId == basePlanId }
            ?.offerToken
            ?: return Result.failure(Exception("Offer not found for base plan: $basePlanId"))

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        pendingPurchase = CompletableDeferred()
        billingClient.launchBillingFlow(activity, billingFlowParams)
        return pendingPurchase!!.await()
    }

    /** Returns the active subscription purchase tokens for the current user, if any. */
    suspend fun getActivePurchases(): List<Purchase> = suspendCancellableCoroutine { cont ->
        if (!billingClient.isReady) {
            cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { _, purchases ->
            cont.resume(purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED })
        }
    }

    /** Acknowledges a purchase — required by Google or it will be refunded after 3 days. */
    suspend fun acknowledgePurchase(purchaseToken: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { result ->
                cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
            }
        }
}
