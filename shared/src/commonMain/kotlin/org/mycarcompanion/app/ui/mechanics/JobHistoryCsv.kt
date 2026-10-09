package org.mycarcompanion.app.ui.mechanics

import org.mycarcompanion.app.data.models.CannedLine
import org.mycarcompanion.app.data.models.HistoryJob
import org.mycarcompanion.app.data.models.maintenanceCategories

/** Columns, in order. One row per part, labor, or fee line; rows for the same job share the first six. */
val historyCsvColumns = listOf("Date", "Customer", "Year", "Make", "Model", "Job", "Type", "Item", "Qty", "Price")

/** Optional trailing columns: the email links the job to the customer's car once they join, and enables invites. */
val historyCsvOptionalColumns = listOf("Email", "VIN", "Mileage")

val historyCsvExample = """
Date,Customer,Year,Make,Model,Job,Type,Item,Qty,Price,Email,VIN,Mileage
2025-03-04,Maria Lopez,2018,Honda,Civic,Front brakes,Labor,Front brake pads and rotors,1.5,120,maria@example.com,,48200
2025-03-04,Maria Lopez,2018,Honda,Civic,Front brakes,Part,Brake pads,1,89.99,,,
2025-03-04,Maria Lopez,2018,Honda,Civic,Front brakes,Part,Rotor,2,64.50,,,
""".trim()

/** The DB function takes at most this many jobs per call. */
const val MAX_HISTORY_JOBS = 500

data class HistoryParse(val jobs: List<HistoryJob>, val errors: List<String>) {
    val lineCount: Int get() = jobs.sumOf { it.lines.size }
    val total: Double get() = jobs.sumOf { j -> j.lines.sumOf { it.quantity * it.unitPrice } }
}

/** Groups CSV rows into jobs. Any bad row is reported, and the caller imports nothing until it's fixed. */
fun parseJobHistoryCsv(raw: String): HistoryParse {
    // Excel's "CSV UTF-8" export starts with a byte-order mark
    val rows = raw.removePrefix("﻿").lines().withIndex().filter { it.value.isNotBlank() }
    val dataRows = if (rows.firstOrNull()?.value?.lowercase()?.contains("date") == true) rows.drop(1) else rows
    val errors = mutableListOf<String>()
    // insertion-ordered, so jobs come out in file order
    val jobs = LinkedHashMap<String, HistoryJob>()

    for ((index, line) in dataRows) {
        val rowNo = index + 1
        val f = splitCsv(line).map { it.trim() }
        fun bad(why: String) { errors += "Row $rowNo: $why" }
        if (f.size < 10) { bad("needs 10 columns, found ${f.size}"); continue }

        val date = toIsoDate(f[0]) ?: run { bad("date '${f[0]}' should look like 2025-03-04 or 3/4/2025"); null }
        val year = f[2].toIntOrNull()?.takeIf { it in 1900..2100 } ?: run { bad("year '${f[2]}' isn't a year"); null }
        if (f[3].isBlank() || f[4].isBlank()) bad("make and model are required")
        val kind = toKind(f[6]) ?: run { bad("type '${f[6]}' should be Labor, Part, or Fee"); null }
        if (f[7].isBlank()) bad("item is required")
        val qty = if (f[8].isBlank()) 1.0 else f[8].toDoubleOrNull()?.takeIf { it > 0 } ?: run { bad("qty '${f[8]}' must be above 0"); null }
        val price = f[9].replace("$", "").replace(",", "").toDoubleOrNull()
            ?.takeIf { it >= 0 || kind == "fee" } ?: run { bad("price '${f[9]}' isn't a valid price (only a Fee can be negative)"); null }
        val email = f.getOrNull(10).orEmpty().ifBlank { null }
        if (email != null && !email.matches(Regex("""[^@\s]+@[^@\s]+\.[^@\s]+"""))) bad("email '$email' doesn't look like an email")
        val vin = f.getOrNull(11).orEmpty().ifBlank { null }
        val mileageRaw = f.getOrNull(12).orEmpty().replace(",", "")
        val mileage = if (mileageRaw.isBlank()) null else mileageRaw.toIntOrNull()?.takeIf { it >= 0 } ?: run { bad("mileage '${f[12]}' should be a whole number"); null }
        if (date == null || year == null || kind == null || qty == null || price == null || f[3].isBlank() || f[4].isBlank() || f[7].isBlank()) continue

        val customer = f[1].ifBlank { "Past customer" }
        val key = listOf(date, customer, year, f[3], f[4], f[5]).joinToString("|").lowercase()
        val line = CannedLine(kind, f[7], qty, price)
        // Email, VIN, and mileage only need to be on one of the job's rows
        jobs[key] = jobs[key]?.let { it.copy(lines = it.lines + line, clientEmail = it.clientEmail ?: email, vin = it.vin ?: vin, mileage = it.mileage ?: mileage) }
            ?: HistoryJob(date, customer, year, f[3], f[4], f[5].ifBlank { null }, listOf(line), email, vin, mileage)
    }
    val categorized = jobs.mapValues { (_, j) -> j.copy(category = guessCategory(listOfNotNull(j.description) + j.lines.map { it.description })) }
    if (jobs.size > MAX_HISTORY_JOBS) errors += "That's ${jobs.size} jobs; import up to $MAX_HISTORY_JOBS at a time by splitting the file."
    return HistoryParse(categorized.values.toList(), errors)
}

// ponytail: first category named in the job or its items, else Other; a keyword map if this guesses badly
private fun guessCategory(texts: List<String>): String {
    val text = texts.joinToString(" ").lowercase()
    return maintenanceCategories.firstOrNull { it != "Other" && text.contains(it.lowercase()) }
        ?: when {
            "brake" in text -> "Brake Service"
            "oil" in text -> "Oil Change"
            "tire" in text -> "Tire Rotation"
            "battery" in text -> "Battery Replacement"
            else -> "Other"
        }
}

private fun toKind(raw: String): String? = when (raw.lowercase().trimEnd('s')) {
    "labor", "labour", "l" -> "labor"
    "part", "p" -> "part"
    "fee", "f", "discount" -> "fee"
    else -> null
}

/** YYYY-MM-DD as is, or a spreadsheet's M/D/YYYY. */
private fun toIsoDate(raw: String): String? {
    Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").matchEntire(raw)?.destructured?.let { (y, m, d) -> return iso(y, m, d) }
    Regex("""(\d{1,2})/(\d{1,2})/(\d{4})""").matchEntire(raw)?.destructured?.let { (m, d, y) -> return iso(y, m, d) }
    return null
}

private fun iso(y: String, m: String, d: String): String? {
    val month = m.toInt()
    val day = d.toInt()
    if (month !in 1..12 || day !in 1..31) return null
    return "$y-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

/** Comma split that respects quotes, including "" for a literal quote inside a quoted field. */
private fun splitCsv(line: String): List<String> {
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' && inQuotes && line.getOrNull(i + 1) == '"' -> { cur.append('"'); i++ }
            c == '"' -> inQuotes = !inQuotes
            c == ',' && !inQuotes -> { out += cur.toString(); cur.clear() }
            else -> cur.append(c)
        }
        i++
    }
    out += cur.toString()
    return out
}
