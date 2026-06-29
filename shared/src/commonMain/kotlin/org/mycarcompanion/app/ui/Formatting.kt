package org.mycarcompanion.app.ui

/** Formats a monetary value to two decimal places (no thousands separator, no currency symbol). */
fun formatMoney(value: Double): String {
    val intPart = value.toLong()
    val fracPart = ((value - intPart) * 100 + 0.5).toLong()
    return "$intPart.${fracPart.toString().padStart(2, '0')}"
}
