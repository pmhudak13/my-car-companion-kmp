package org.mycarcompanion.app.ui.mechanics

import org.mycarcompanion.app.data.models.MechanicJob
import kotlin.test.Test
import kotlin.test.assertEquals

class PastJobsFilterTest {

    private val open = MechanicJob(id = "open", clientName = "Ann Lee", status = "open")
    private val old = MechanicJob(
        id = "old", clientName = "Ann Lee", clientEmail = "ann@x.com", status = "completed",
        vehicleVin = "1HGCM82633A004352", vehicleLicensePlate = "7ABC123", completedAt = "2026-01-01",
    )
    private val newer = MechanicJob(id = "newer", clientName = "Bob Ruiz", status = "completed", imported = true, createdAt = "2026-06-01")
    private val jobs = listOf(open, old, newer)

    private fun ids(query: String) = filterPastJobs(jobs, query).map { it.id }

    @Test
    fun blankQueryListsCompletedAndImportedNewestFirst() = assertEquals(listOf("newer", "old"), ids(" "))

    @Test
    fun matchesEachSearchableFieldIgnoringCaseSpacesAndDashes() {
        assertEquals(listOf("old"), ids("ann l"))
        assertEquals(listOf("old"), ids("ANN@X"))
        assertEquals(listOf("old"), ids("1hg-cm 826"))
        assertEquals(listOf("old"), ids("7abc 123"))
    }

    @Test
    fun noMatchIsEmpty() = assertEquals(emptyList(), ids("zzz"))
}
