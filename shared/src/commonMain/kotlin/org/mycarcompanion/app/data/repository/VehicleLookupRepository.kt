package org.mycarcompanion.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.mycarcompanion.app.data.models.carMakes

data class DecodedVin(val year: Int?, val make: String, val model: String)

data class VehiclePhotoRead(val plate: String?, val vin: String?)

@Serializable
private data class VpicResult(
    @SerialName("ModelYear") val modelYear: String = "",
    @SerialName("Make") val make: String = "",
    @SerialName("Model") val model: String = "",
)

@Serializable
private data class VpicResponse(@SerialName("Results") val results: List<VpicResult> = emptyList())

@Serializable
private data class PhotoReadResponse(val plate: String? = null, val vin: String? = null)

private val VIN_CHARS = Regex("[A-HJ-NPR-Z0-9]")

/**
 * Cleans a typed or scanned VIN: uppercases, drops anything that can't be in a VIN, and keeps the
 * last 17 characters (door-jamb Code 39 barcodes often carry a leading "I" import marker).
 * Returns null when fewer than 17 valid characters remain.
 */
fun normalizeVin(raw: String): String? {
    val chars = raw.uppercase().filter { VIN_CHARS.matches(it.toString()) }
    return if (chars.length < 17) null else chars.takeLast(17)
}

/** NHTSA spells makes in caps ("FORD"); use the app's own spelling when it has one. */
internal fun appMakeName(nhtsaMake: String): String =
    carMakes.firstOrNull { it.equals(nhtsaMake, ignoreCase = true) }
        ?: nhtsaMake.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

/** VIN decode via NHTSA vPIC (free, no key, CORS allowed) and plate/VIN reading from a photo via AI. */
class VehicleLookupRepository(
    private val client: SupabaseClient,
    private val http: HttpClient = HttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun decodeVin(rawVin: String): Result<DecodedVin> = runCatching {
        val vin = normalizeVin(rawVin) ?: error("A VIN has 17 letters and numbers (no I, O or Q)")
        val body = http.get("https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValues/$vin?format=json").bodyAsText()
        val r = json.decodeFromString<VpicResponse>(body).results.firstOrNull()
        // vPIC still answers for a VIN with a bad check digit, so trust it only when it names a make
        if (r == null || r.make.isBlank()) error("Couldn't find that VIN. Check it and try again.")
        DecodedVin(year = r.modelYear.toIntOrNull(), make = appMakeName(r.make.trim()), model = r.model.trim())
    }

    suspend fun readPhoto(imageBase64: String, mimeType: String): Result<VehiclePhotoRead> = runCatching {
        val body = client.functions.invoke(
            "read-vehicle-photo",
            body = buildJsonObject {
                put("image_base64", imageBase64)
                put("mime_type", mimeType)
            },
        ).bodyAsText()
        val serverError = (runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()?.get("error") as? JsonPrimitive)?.content
        if (serverError != null) error(serverError)
        val r = json.decodeFromString<PhotoReadResponse>(body)
        if (r.plate.isNullOrBlank() && r.vin.isNullOrBlank()) error("Couldn't read a plate or VIN in that photo. Try a closer, sharper shot.")
        VehiclePhotoRead(plate = r.plate?.takeIf { it.isNotBlank() }, vin = r.vin?.let(::normalizeVin))
    }.recoverCatching { e ->
        // non-2xx arrives as an exception carrying the function's JSON; show something readable
        val raw = e.message.orEmpty()
        val msg = Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1) ?: raw
        error(if (msg.startsWith("Couldn't") || msg.startsWith("Photo")) msg else "Couldn't read that photo. Please try again.")
    }
}
