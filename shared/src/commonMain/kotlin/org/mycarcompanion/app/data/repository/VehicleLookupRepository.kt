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

@Serializable
private data class VpicResult(
    @SerialName("ModelYear") val modelYear: String = "",
    @SerialName("Make") val make: String = "",
    @SerialName("Model") val model: String = "",
    @SerialName("ErrorCode") val errorCode: String = "",
)

@Serializable
private data class VpicResponse(@SerialName("Results") val results: List<VpicResult> = emptyList())

@Serializable
private data class PhotoReadResponse(val plate: String? = null)

private val VIN_CHARS = Regex("[A-HJ-NPR-Z0-9]")

/**
 * Cleans a typed or scanned VIN: uppercases, drops anything that can't be in a VIN, and picks the
 * 17-character run whose check digit adds up (door-jamb Code 39 barcodes often carry a leading "I"
 * import marker or trailing data), else the last 17. Returns null when fewer than 17 remain.
 */
fun normalizeVin(raw: String): String? {
    val chars = raw.uppercase().filter { VIN_CHARS.matches(it.toString()) }
    if (chars.length < 17) return null
    return chars.windowed(17).lastOrNull(::vinCheckDigitOk) ?: chars.takeLast(17)
}

private const val VIN_LETTERS = "ABCDEFGHJKLMNPRSTUVWXYZ"
private val VIN_LETTER_VALUES = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 1, 2, 3, 4, 5, 7, 9, 2, 3, 4, 5, 6, 7, 8, 9)
private val VIN_WEIGHTS = intArrayOf(8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2)

/** The 9th character of every VIN sold in North America is a checksum of the rest; most single misreads fail it. */
fun vinCheckDigitOk(vin: String): Boolean {
    if (vin.length != 17) return false
    val sum = vin.withIndex().sumOf { (i, c) ->
        val value = if (c.isDigit()) c.digitToInt() else VIN_LETTER_VALUES.getOrNull(VIN_LETTERS.indexOf(c)) ?: return false
        value * VIN_WEIGHTS[i]
    }
    val expected = (sum % 11).let { if (it == 10) 'X' else it.digitToChar() }
    return vin[8] == expected
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
        // vPIC still decodes a VIN with a wrong check digit, just to the wrong year/model
        if (!vinCheckDigitOk(vin)) error("That VIN doesn't add up, so a character is probably wrong. Check it against the car.")
        val body = http.get("https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValues/$vin?format=json").bodyAsText()
        val r = json.decodeFromString<VpicResponse>(body).results.firstOrNull()
        if (r == null || r.make.isBlank()) error("Couldn't find that VIN. Check it and try again.")
        // code 5 = "errors in few positions": Code 39 scans can read "A" as "1" (same bars), which the
        // check digit can't catch and which turned a 2010 Focus into a 2001
        if ("5" in r.errorCode.split(",").map { it.trim() }) error("That VIN doesn't match a real vehicle, so a character is probably wrong. Check it against the car.")
        DecodedVin(year = r.modelYear.toIntOrNull(), make = appMakeName(r.make.trim()), model = r.model.trim())
    }

    /** Reads only the license plate; VINs come from the barcode scanner, which the check digit can verify. */
    suspend fun readPlatePhoto(imageBase64: String, mimeType: String): Result<String> = runCatching {
        val body = client.functions.invoke(
            "read-vehicle-photo",
            body = buildJsonObject {
                put("image_base64", imageBase64)
                put("mime_type", mimeType)
            },
        ).bodyAsText()
        val serverError = (runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()?.get("error") as? JsonPrimitive)?.content
        if (serverError != null) error(serverError)
        json.decodeFromString<PhotoReadResponse>(body).plate?.takeIf { it.isNotBlank() }
            ?: error("Couldn't read a plate in that photo. Try a closer, sharper shot.")
    }.recoverCatching { e ->
        // non-2xx arrives as an exception carrying the function's JSON; show something readable
        val raw = e.message.orEmpty()
        val msg = Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1) ?: raw
        error(if (msg.startsWith("Couldn't") || msg.startsWith("Photo")) msg else "Couldn't read that photo. Please try again.")
    }
}
