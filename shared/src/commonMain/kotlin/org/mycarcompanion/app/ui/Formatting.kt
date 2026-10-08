package org.mycarcompanion.app.ui

import kotlin.math.abs
import kotlin.math.round

/** Formats a monetary value to two decimal places (no thousands separator, no currency symbol). */
fun formatMoney(value: Double): String {
    // whole cents first, so -10.50 doesn't become "-10.-49" and 9.999 doesn't become "9.100"
    val cents = round(abs(value) * 100).toLong()
    val sign = if (value < 0 && cents != 0L) "-" else ""
    return "$sign${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

/** Dollar amount with the minus in front of the symbol: -$10.00, not $-10.00. */
fun formatUsd(value: Double): String {
    val amount = formatMoney(value)
    return if (amount.startsWith("-")) "-$${amount.drop(1)}" else "$$amount"
}
