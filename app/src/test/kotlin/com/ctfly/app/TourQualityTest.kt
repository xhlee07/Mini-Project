package com.ctfly.app

import org.junit.Assert.*
import org.junit.Test

class TourQualityTest {
    @Test
    fun ultraRetainsNativePixelsAndEveryModeRespectsBudget() {
        assertEquals(2992 to 900, TourQuality.ULTRA.size(2992, 900))
        for (mode in TourQuality.entries) for ((w, h) in
            listOf(1 to 1, 1080 to 1920, 2992 to 1344, 8192 to 4096)) {
            val (rw, rh) = mode.size(w, h)
            assertTrue(rw in 1..w && rh in 1..h)
            assertEquals(w.toDouble() / h, rw.toDouble() / rh, .005)
        }
        assertTrue(TourQuality.BALANCED.size(2992, 1344).first <= 1920)
        assertTrue(TourQuality.SMOOTH.size(2992, 1344).first <= 1280)
        val (w, h) = TourQuality.ULTRA.size(8192, 4096)
        assertTrue(w.toLong() * h <= 6_010_000)
        assertEquals(TourQuality.ULTRA, TourQuality.parse("old setting"))
    }
}
