package org.mycarcompanion.app.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Recall(
    @SerialName("NHTSACampaignNumber") val campaignNumber: String = "",
    @SerialName("ReportReceivedDate") val reportDate: String = "",
    @SerialName("Component") val component: String = "",
    @SerialName("Summary") val summary: String = "",
    @SerialName("Consequence") val consequence: String = "",
    @SerialName("Remedy") val remedy: String = "",
    @SerialName("parkIt") val parkIt: Boolean = false,
)

@Serializable
private data class RecallResponse(val results: List<Recall> = emptyList())

/** Public NHTSA recall lookup by make, model and year (no key, CORS allowed). */
class RecallRepository(private val http: HttpClient = HttpClient()) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getRecalls(make: String, model: String, year: Int): Result<List<Recall>> = runCatching {
        val body = http.get("https://api.nhtsa.gov/recalls/recallsByVehicle") {
            parameter("make", make)
            parameter("model", model)
            parameter("modelYear", year)
        }.bodyAsText()
        json.decodeFromString<RecallResponse>(body).results
    }
}
