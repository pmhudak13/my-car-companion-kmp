package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.mycarcompanion.app.data.supabase.SupabaseConfig

class SubscriptionRepository(private val client: SupabaseClient) {

    // Price IDs from Stripe dashboard
    companion object {
        const val PRICE_PREMIUM_MONTHLY = "price_1TFf16EEo6NSMXCB9SQ29WiZ"
        const val PRICE_PREMIUM_YEARLY  = "price_1TFfCmEEo6NSMXCBhvpPp5ut"
        const val PRICE_MECHANIC_MONTHLY = "price_1TFfAGEEo6NSMXCBu8hSzWVq"
        const val PRICE_MECHANIC_YEARLY  = "price_1TFfCIEEo6NSMXCBXTQaAI5x"
    }

    /** Returns the Stripe Billing Portal URL for the current user's subscription management. */
    suspend fun createPortalSession(): Result<String> = runCatching {
        // Guard: ensure the user is authenticated before calling the edge function.
        // The SDK automatically includes Authorization: Bearer <session-token> on the request,
        // so the edge function receives the correct JWT without us needing to add it manually.
        // (Adding a second Authorization header via custom headers caused Ktor to join them
        // with ", " producing an invalid JWT — see project memory for history.)
        client.auth.currentSessionOrNull()
            ?: error("Session expired — please sign out and sign back in")
        val response = client.functions.invoke(
            function = "create-portal",
            body = buildJsonObject {
                put("return_url", SupabaseConfig.portalReturnUrl)
            },
        )
        parseUrlFromResponse(response.bodyAsText())
    }

    /** Returns the Stripe Checkout URL for the given price ID, or throws on error. */
    suspend fun createCheckoutSession(priceId: String): Result<String> = runCatching {
        client.auth.currentSessionOrNull()
            ?: error("Session expired — please sign out and sign back in")
        val response = client.functions.invoke(
            function = "create-checkout",
            body = buildJsonObject {
                put("price_id", priceId)
                put("success_url", SupabaseConfig.checkoutSuccessUrl)
                put("cancel_url", SupabaseConfig.checkoutCancelUrl)
            },
        )
        parseUrlFromResponse(response.bodyAsText())
    }

    private fun parseUrlFromResponse(body: String): String {
        val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
        val serverError = (json?.get("error") as? JsonPrimitive)?.content
        if (serverError != null) error(serverError)
        return (json?.get("url") as? JsonPrimitive)?.content
            ?: error("Unexpected response from server")
    }
}
