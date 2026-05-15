package org.mycarcompanion.app.ui.mechanics

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.ImportTab
import org.mycarcompanion.app.data.models.ImportedRecord
import org.mycarcompanion.app.data.models.MechanicJobLogInsert
import org.mycarcompanion.app.data.models.maintenanceCategories
import org.mycarcompanion.app.data.repository.MechanicJobRepository

data class RecordImportState(
    val activeTab: ImportTab = ImportTab.CSV,
    val isParsing: Boolean = false,
    val isScanning: Boolean = false,
    val isSaving: Boolean = false,
    val parsedRecords: List<ImportedRecord> = emptyList(),
    val error: String? = null,
    val savedCount: Int = 0,
    val saveSuccess: Boolean = false,
)

class RecordImportScreenModel(
    private val repo: MechanicJobRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(RecordImportState())
    val state: StateFlow<RecordImportState> = _state.asStateFlow()

    fun selectTab(tab: ImportTab) {
        _state.update { it.copy(activeTab = tab, parsedRecords = emptyList(), error = null, saveSuccess = false) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    // ── CSV ──────────────────────────────────────────────────────────────────

    fun onCsvContent(content: String) {
        _state.update { it.copy(isParsing = true, error = null, parsedRecords = emptyList()) }
        val records = parseCsvContent(content)
        if (records.isEmpty()) {
            _state.update { it.copy(isParsing = false, error = "No records found. Make sure the file matches the expected format.") }
        } else {
            _state.update { it.copy(isParsing = false, parsedRecords = records) }
        }
    }

    // ── AI Scan ──────────────────────────────────────────────────────────────

    fun onInvoiceImage(base64: String, mimeType: String, jobId: String) {
        _state.update { it.copy(isScanning = true, error = null, parsedRecords = emptyList()) }
        screenModelScope.launch {
            repo.parseInvoiceRecords(base64, mimeType, jobId).fold(
                onSuccess = { records ->
                    if (records.isEmpty()) {
                        _state.update { it.copy(isScanning = false, error = "No service records found in this image. Try a clearer photo of the invoice.") }
                    } else {
                        _state.update { it.copy(isScanning = false, parsedRecords = records) }
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(isScanning = false, error = "Could not analyze image: ${e.message}") }
                },
            )
        }
    }

    // ── Import ───────────────────────────────────────────────────────────────

    fun importRecords(jobId: String, clientEmail: String?) {
        val valid = _state.value.parsedRecords.filter { it.isValid }
        if (valid.isEmpty()) return
        _state.update { it.copy(isSaving = true, error = null) }
        screenModelScope.launch {
            val inserts = valid.map { r ->
                MechanicJobLogInsert(
                    mechanicJobId = jobId,
                    mechanicUserId = "",  // repo fills this in from the current session
                    category = r.category,
                    description = r.description,
                    date = r.date,
                    mileage = r.mileage,
                    cost = r.cost,
                    notes = r.notes,
                )
            }
            repo.addLogsInBulk(inserts, clientEmail).fold(
                onSuccess = { count ->
                    _state.update { it.copy(isSaving = false, savedCount = count, saveSuccess = true, parsedRecords = emptyList()) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isSaving = false, error = "Import failed: ${e.message}") }
                },
            )
        }
    }

    // ── CSV parsing ──────────────────────────────────────────────────────────

    private fun parseCsvContent(raw: String): List<ImportedRecord> {
        val lines = raw.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        // Skip header row if it contains "date" or "category"
        val dataLines = if (lines.first().lowercase().contains("date") ||
            lines.first().lowercase().contains("category")) lines.drop(1) else lines
        return dataLines.mapNotNull { parseCsvLine(it) }
    }

    private fun parseCsvLine(line: String): ImportedRecord? {
        val fields = splitCsvLine(line)
        if (fields.size < 3) return null  // date + category + description minimum

        val date = fields.getOrNull(0)?.trim().orEmpty()
        val category = fields.getOrNull(1)?.trim().orEmpty()
        val description = fields.getOrNull(2)?.trim().orEmpty()
        val mileageStr = fields.getOrNull(3)?.trim().orEmpty()
        val costStr = fields.getOrNull(4)?.trim().orEmpty()
        val notes = fields.getOrNull(5)?.trim()?.ifBlank { null }

        val dateValid = date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
        // Fuzzy category match: exact first, then case-insensitive, then "Other"
        val resolvedCategory = maintenanceCategories.find { it == category }
            ?: maintenanceCategories.find { it.lowercase() == category.lowercase() }
            ?: "Other"
        val mileage = mileageStr.toIntOrNull() ?: 0
        val cost = costStr.replace("$", "").toDoubleOrNull()

        val isValid = dateValid && description.isNotBlank()
        val validationError = when {
            !dateValid -> "Invalid date '$date' — expected YYYY-MM-DD"
            description.isBlank() -> "Description is required"
            else -> null
        }

        return ImportedRecord(
            date = date,
            category = resolvedCategory,
            description = description,
            mileage = mileage,
            cost = cost,
            notes = notes,
            isValid = isValid,
            validationError = validationError,
        )
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { result.add(current.toString()); current.clear() }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }
}
