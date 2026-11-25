package org.mycarcompanion.app.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun checkDigitCatchesOneMisreadCharacter() {
        assertTrue(vinCheckDigitOk("1FAHP3FN1AW134670")) // 2010 Ford Focus
        assertFalse(vinCheckDigitOk("1FAHP3FN1AV134670")) // "W" misread as "V"
        // "A" vs "1" share a value, so that misread passes here and is caught by vPIC's error code 5
        assertTrue(vinCheckDigitOk("1FAHP3FN11W134670"))
    }

    @Test
    fun picksTheWindowWithAValidCheckDigit() {
        assertEquals("1FAHP3FN1AW134670", normalizeVin("1FAHP3FN1AW1346702")) // trailing barcode data
    }

    @Test
    fun usesAppMakeSpelling() {
        assertEquals("Ford", appMakeName("FORD"))
        assertEquals("GMC", appMakeName("GMC"))
        assertEquals("Some Make", appMakeName("SOME MAKE"))
    }
}
