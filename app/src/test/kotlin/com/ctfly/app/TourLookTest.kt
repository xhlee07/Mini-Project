package com.ctfly.app

import org.junit.Assert.*
import org.junit.Test

class TourLookTest {
    @Test
    fun repeatedTurnsCrossFullCircleAndResetWithoutJumping() {
        val look = TourLook()
        repeat(20) {
            look.add(0.5f, 0f)
            repeat(12) { look.step(1f / 60f) }
        }
        assertTrue(look.yaw > 9f)
        look.add(0f, 100f)
        repeat(60) { look.step(1f / 60f) }
        assertEquals(1.1f, look.pitch, 0.001f)
        look.reset(-0.2f, -0.3f)
        look.step(1f / 60f)
        assertEquals(-0.2f, look.yaw, 0.001f)
        assertEquals(-0.3f, look.pitch, 0.001f)
    }

    @Test
    fun easingHasSameResponseAtThirtyAndOneTwentyFps() {
        val slow = TourLook()
        val fast = TourLook()
        slow.add(1f, 0.4f)
        fast.add(1f, 0.4f)
        repeat(6) { slow.step(1f / 30f) }
        repeat(24) { fast.step(1f / 120f) }
        assertEquals(slow.yaw, fast.yaw, 0.0001f)
        assertTrue(slow.yaw > 0.99f)
    }
}
