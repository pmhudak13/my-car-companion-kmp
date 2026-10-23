package org.mycarcompanion.app.ui.mileage

import org.mycarcompanion.app.data.models.MileageTrip
import org.mycarcompanion.app.data.models.tripPurposeLabels
import org.mycarcompanion.app.data.models.tripPurposes
import org.mycarcompanion.app.ui.formatMoney

/** IRS standard mileage rates in cents per mile. Source: irs.gov/tax-professionals/standard-mileage-rates */
private class IrsRate(val from: String, val business: Double, val medical: Double, val charity: Double)

// shortcut: hardcoded table; add a row (and move RATES_KNOWN_THROUGH) when the IRS announces new rates
private val irsRates = listOf(
    IrsRate("2026-07-01", 76.0, 23.5, 14.0),
    IrsRate("2026-01-01", 72.5, 20.5, 14.0),
    IrsRate("2025-01-01", 70.0, 21.0, 14.0),
    IrsRate("2024-01-01", 67.0, 21.0, 14.0),
)
private const val RATES_KNOWN_THROUGH = "2026-12-31"

/** Cents per mile for a trip purpose on a YYYY-MM-DD date, or null when there is no deduction or no rate on file. */
fun irsCentsPerMile(purpose: String, date: String): Double? {
    if (date > RATES_KNOWN_THROUGH) return null
    val rate = irsRates.firstOrNull { date >= it.from } ?: return null
    return when (purpose) {
        "business" -> rate.business
        "medical" -> rate.medical
        "charity" -> rate.charity
        else -> null
    }
}

/** Years (newest first) that have at least one finished trip. */
fun mileageReportYears(trips: List<MileageTrip>): List<Int> =
    trips.filter { it.endedAt != null }
        .mapNotNull { it.startedAt.take(4).toIntOrNull() }
        .distinct()
        .sortedDescending()

/** Trip log plus per-purpose totals for one tax year, as CSV. */
fun buildMileageCsv(trips: List<MileageTrip>, vehicleNames: Map<String, String>, year: Int): String {
    val yearTrips = trips
        .filter { it.endedAt != null && it.startedAt.startsWith("$year-") }
        .sortedBy { it.startedAt }

    val lines = mutableListOf("Date,Purpose,Vehicle,Miles,IRS Rate (cents/mi),Deduction ($),Notes")
    val milesByPurpose = mutableMapOf<String, Double>()
    val deductionByPurpose = mutableMapOf<String, Double>()
    val tripsByPurpose = mutableMapOf<String, Int>()
    var missingRate = false

    for (trip in yearTrips) {
        val date = trip.startedAt.take(10)
        val cents = irsCentsPerMile(trip.purpose, date)
        if (cents == null && trip.purpose != "personal") missingRate = true
        val deduction = cents?.let { trip.distanceMiles * it / 100 }
        milesByPurpose[trip.purpose] = (milesByPurpose[trip.purpose] ?: 0.0) + trip.distanceMiles
        tripsByPurpose[trip.purpose] = (tripsByPurpose[trip.purpose] ?: 0) + 1
        if (deduction != null) deductionByPurpose[trip.purpose] = (deductionByPurpose[trip.purpose] ?: 0.0) + deduction
        lines += listOf(
            date,
            tripPurposeLabels[trip.purpose] ?: trip.purpose,
            trip.vehicleId?.let { vehicleNames[it] }.orEmpty(),
            formatMoney(trip.distanceMiles),
            cents?.let(::formatRate).orEmpty(),
            deduction?.let(::formatMoney).orEmpty(),
            trip.notes.orEmpty(),
        ).joinToString(",") { csvField(it) }
    }

    lines += ""
    lines += "Summary for $year"
    lines += "Purpose,Trips,Miles,Deduction ($)"
    val purposes = tripPurposes + (tripsByPurpose.keys - tripPurposes.toSet())
    for (purpose in purposes) {
        val count = tripsByPurpose[purpose] ?: continue
        lines += listOf(
            tripPurposeLabels[purpose] ?: purpose,
            count.toString(),
            formatMoney(milesByPurpose.getValue(purpose)),
            deductionByPurpose[purpose]?.let(::formatMoney).orEmpty(),
        ).joinToString(",") { csvField(it) }
    }
    lines += listOf(
        "Total",
        yearTrips.size.toString(),
        formatMoney(milesByPurpose.values.sum()),
        formatMoney(deductionByPurpose.values.sum()),
    ).joinToString(",")
    if (missingRate) {
        lines += ""
        lines += csvField("Some trips have no IRS rate on file in this app, so their deduction is blank.")
    }
    return lines.joinToString("\r\n") + "\r\n"
}

private fun formatRate(cents: Double): String = formatMoney(cents).trimEnd('0').trimEnd('.')

private fun csvField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${value.replace("\"", "\"\"")}\"" else value
