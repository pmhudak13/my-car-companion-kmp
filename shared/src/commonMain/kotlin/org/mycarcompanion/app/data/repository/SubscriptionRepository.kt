package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SubscriptionRepository(private val client: SupabaseClient) {

    // Price IDs from Stripe dashboard
    companion object {
        const val PRICE_PREMIUM_MONTHLY = "price_1TFf16EEo6NSMXCB9SQ29WiZ"
        const val PRICE_PREMIUM_YEARLY  = "price_1TFfCmEEo6NSMXCBhvpPp5ut"
        const val PRICE_MECHANIC_MONTHLY = "price_1TFfAGEEo6NSMXCBu8hSzWVq"
        const val PRICE_MECHANIC_YEARLY  = "price_1TFfCIEEo6NSMXCBXTQaAI5x"
    }

    /** Returns the Stripe Checkout URL for the given price ID, or throws on error. */
    suspend fun createCheckoutSession(priceId: String): Result<String> = runCatching {
        val session = client.auth.currentSessionOrNull()
            ?: error("Session expired — please sign out and sign back in")
        val response = client.functions.invoke(
            function = "create-checkout",
            body = buildJsonObject {
                put("price_id", priceId)
                put("success_url", "org.mycarcompanion.app://subscription/success")
                put("cancel_url", "org.mycarcompanion.app://subscription/cancel")
            },
            headers = Headers.build {
                append("Authorization", "Bearer ${session.accessToken}")
            },
        )
        val body = response.bodyAsText()
        val json = Json.parseToJsonElement(body).jsonObject
        json["url"]?.jsonPrimitive?.content
            ?: error("Missing url in response: $body")
    }
}
