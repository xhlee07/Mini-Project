package com.ctfly.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    @Test
    fun ageBoundaryUsesBirthday() {
        val today = LocalDate.of(2026, 10, 2)
        assertTrue(adult("2008-10-02", today))
        assertFalse(adult("2008-10-03", today))
        assertFalse(adult("2030-01-01", today))
        assertFalse(adult("invalid", today))
        assertTrue(adult("2004-02-29", LocalDate.of(2022, 3, 1)))
    }

    @Test
    fun pricingUsesIntegerSen() {
        assertEquals(900, bookingPrice(600, 90))
        assertEquals(3750, bookingPrice(2500, 90))
        assertEquals("RM 6.50", money(650))
        assertEquals("Bronze", tier(29999))
        assertEquals("Silver", tier(30000))
        assertEquals("Gold", tier(100000))
    }
}
