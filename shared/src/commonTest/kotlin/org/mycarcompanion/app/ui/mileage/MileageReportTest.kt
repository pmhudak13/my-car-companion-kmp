package org.mycarcompanion.app.ui.mileage

import org.mycarcompanion.app.data.models.MileageTrip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MileageReportTest {

    private fun trip(id: String, purpose: String, miles: Double, startedAt: String, notes: String? = null, ended: Boolean = true) =
        MileageTrip(
            id = id,
            vehicleId = "v1",
            purpose = purpose,
            notes = notes,
            distanceMiles = miles,
            startedAt = startedAt,
            endedAt = if (ended) startedAt else null,
        )

    @Test
    fun ratesFollowTheIrsMidYearChange() {
        assertEquals(72.5, irsCentsPerMile("business", "2026-06-30"))
        assertEquals(76.0, irsCentsPerMile("business", "2026-07-01"))
        assertEquals(20.5, irsCentsPerMile("medical", "2026-03-01"))
        assertEquals(14.0, irsCentsPerMile("charity", "2025-05-01"))
        assertNull(irsCentsPerMile("personal", "2026-03-01"))
        assertNull(irsCentsPerMile("business", "2027-01-01"))
        assertNull(irsCentsPerMile("business", "2023-12-31"))
    }

    @Test
    fun csvHasRowsSummaryAndOnlyTheSelectedYear() {
        val trips = listOf(
            trip("a", "business", 10.0, "2026-07-02T15:00:00Z", notes = "Client, \"Acme\""),
            trip("b", "business", 100.0, "2026-02-01T15:00:00Z"),
            trip("c", "personal", 5.0, "2026-03-01T15:00:00Z"),
            trip("d", "business", 50.0, "2025-12-01T15:00:00Z"),
            trip("e", "business", 7.0, "2026-04-01T15:00:00Z", ended = false),
        )
        val lines = buildMileageCsv(trips, mapOf("v1" to "2018 Honda Civic"), 2026).split("\r\n")

        assertEquals("Date,Purpose,Vehicle,Miles,IRS Rate (cents/mi),Deduction ($),Notes", lines[0])
        assertEquals("2026-02-01,Business,2018 Honda Civic,100.00,72.5,72.50,", lines[1])
        assertEquals("2026-03-01,Personal,2018 Honda Civic,5.00,,,", lines[2])
        assertEquals("2026-07-02,Business,2018 Honda Civic,10.00,76,7.60,\"Client, \"\"Acme\"\"\"", lines[3])
        assertTrue("Business,2,110.00,80.10" in lines)
        assertTrue("Personal,1,5.00," in lines)
        assertTrue("Total,3,115.00,80.10" in lines)
        assertTrue(lines.none { it.startsWith("2025") })
    }

    @Test
    fun yearsAreNewestFirstAndSkipActiveTrips() {
        val trips = listOf(
            trip("a", "business", 1.0, "2025-01-01T00:00:00Z"),
            trip("b", "business", 1.0, "2026-01-01T00:00:00Z"),
            trip("c", "business", 1.0, "2024-01-01T00:00:00Z", ended = false),
        )
        assertEquals(listOf(2026, 2025), mileageReportYears(trips))
    }
}
