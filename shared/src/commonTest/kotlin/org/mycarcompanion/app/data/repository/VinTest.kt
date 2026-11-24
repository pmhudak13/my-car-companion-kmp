package org.mycarcompanion.app.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VinTest {

    @Test
    fun cleansTypedVin() {
        assertEquals("1FAHP3F20CL148530", normalizeVin(" 1fahp3f2-0cl148530 "))
    }

    @Test
    fun dropsCode39ImportPrefix() {
        assertEquals("1FAHP3F20CL148530", normalizeVin("I1FAHP3F20CL148530"))
    }

    @Test
    fun rejectsShortVin() {
        assertNull(normalizeVin("1FAHP3F20CL1485"))
    }

    @Test
    fun usesAppMakeSpelling() {
        assertEquals("Ford", appMakeName("FORD"))
        assertEquals("GMC", appMakeName("GMC"))
        assertEquals("Some Make", appMakeName("SOME MAKE"))
    }
}
