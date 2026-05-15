package org.mycarcompanion.app.data.models

data class ImportedRecord(
    val date: String,
    val category: String,
    val description: String,
    val mileage: Int,
    val cost: Double?,
    val notes: String?,
    val isValid: Boolean,
    val validationError: String? = null,
)

enum class ImportTab { CSV, AI_SCAN }
