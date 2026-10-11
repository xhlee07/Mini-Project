package com.ctfly.app

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class TourExperienceTest {
    @Test
    fun lowFrameRateKeepsJumpAndMovementInRealTime() {
        val slow = TourJump()
        val fast = TourJump()
        slow.trigger()
        fast.trigger()
        repeat(3) { slow.step(.5f) }
        repeat(180) { fast.step(1f / 120) }
        assertEquals(0f, slow.height, .001f)
        assertEquals(fast.height, slow.height, .001f)
        val start = TourPose(0f, 0f)
        val moved = TourPlan.move(start, 0f, 1f, .5f, emptyList())
        assertEquals(-2.25f, moved.z, .001f)
    }

    @Test
    fun brakeSteeringDriftsButBrakeAloneStopsAndReverseRecovers() {
        val yaw = KartTrack.startYaw
        val state =
            KartState(countdown = 0f, speed = 12f, vx = sin(yaw) * 12f, vz = -cos(yaw) * 12f)
        val brake = KartPhysics.step(state, 0f, 0f, 1f, false, .1f)
        val slide = KartPhysics.step(state, .8f, 0f, 1f, false, .1f)
        assertFalse(brake.drifting)
        assertTrue(slide.drifting)
        assertTrue(slide.speed > brake.speed + .5f)
        val edge = KartTrack.edge(0, KartTrack.driveLimit - .02f)
        var reverse = KartState(x = edge.x, z = edge.z, yaw = 0f, countdown = 0f)
        val before = KartTrack.project(reverse.x, reverse.z).distance
        repeat(60) { reverse = KartPhysics.step(reverse, 0f, 0f, 0f, false, 1f / 60, 1f) }
        assertTrue(reverse.speed < -3f)
        assertTrue(KartTrack.project(reverse.x, reverse.z).distance < before - 1f)
    }

    @Test
    fun kerbPenaltyIsGentleAndIndependentOfFrameRate() {
        val edge = KartTrack.edge(0, 4.65f)
        val yaw = KartTrack.startYaw
        val initial =
            KartState(
                x = edge.x,
                z = edge.z,
                yaw = yaw,
                speed = 14f,
                vx = sin(yaw) * 14f,
                vz = -cos(yaw) * 14f,
                countdown = 0f,
            )
        var slow = initial
        var fast = initial
        repeat(30) { slow = KartPhysics.step(slow, 0f, 1f, 0f, false, 1f / 30) }
        repeat(120) { fast = KartPhysics.step(fast, 0f, 1f, 0f, false, 1f / 120) }
        assertTrue("Kerb speed ${slow.speed}", slow.speed > 9f)
        assertTrue(hypot(slow.x - initial.x, slow.z - initial.z) > 8f)
        assertEquals(slow.speed, fast.speed, .1f)
        assertEquals(slow.x, fast.x, .1f)
        assertEquals(slow.z, fast.z, .1f)
    }

    @Test
    fun privateRoomsMatchWholeRoomCapacitiesAndPrices() {
        assertEquals(9, PrivateRoomPlan.rooms.size)
        assertEquals(21, PrivateRoomPlan.rooms.sumOf { it.capacity })
        for (room in PrivateRoomPlan.rooms) {
            assertTrue(room.capacity in listOf(1, 2, 4))
            assertEquals(
                when (room.capacity) {
                    1 -> 1500
                    2 -> 2800
                    else -> 5200
                },
                bookingPrice(room.rate, 60),
            )
        }
    }

    @Test
    fun fasterWalkingStillStopsAtThinWalls() {
        val p = TourPose(0f, 0f)
        val wall = listOf(TourObstacle(0f, -.5f, 4f, .07f))
        var walking = p
        var running = p
        repeat(120) {
            walking = TourPlan.move(walking, 0f, 1f, 1f / 60, emptyList())
            running = TourPlan.move(running, 0f, 1f, 1f / 60, emptyList(), 7.2f)
        }
        assertEquals(9f, -walking.z, .02f)
        assertEquals(14.4f, -running.z, .02f)
        var blocked = p
        repeat(30) { blocked = TourPlan.move(blocked, 0f, 1f, .1f, wall, 7.2f) }
        assertTrue(blocked.z > -.23f)
    }

    @Test
    fun jumpRisesLandsAndCannotBeStacked() {
        val jump = TourJump()
        jump.trigger()
        var peak = 0f
        repeat(150) {
            jump.step(1f / 120)
            if (it < 60) jump.trigger()
            peak = max(peak, jump.height)
        }
        assertTrue(peak in 1.2f..1.3f)
        assertEquals(0f, jump.height, 0f)
        jump.trigger()
        jump.step(.1f)
        assertTrue(jump.height > .4f)
    }

    @Test
    fun cubicleDoorActuallyControlsPassageAndCannotCloseThroughPlayer() {
        val x = WashroomDoors.hinges[1] + WashroomDoors.width / 2
        val p = TourPose(x, WashroomDoors.z, area = TourArea.LOUNGE)
        assertFalse(TourPlan.canStand(p.x, p.z, listOf(WashroomDoors.obstacle(1, false)), p.area))
        assertTrue(TourPlan.canStand(p.x, p.z, listOf(WashroomDoors.obstacle(1, true)), p.area))
        assertFalse(WashroomDoors.canClose(1, p))
        assertEquals(1, WashroomDoors.nearest(p))
        assertTrue(WashroomDoors.canClose(1, p.copy(z = p.z + 1)))
    }

    @Test
    fun kartCountdownAccelerationBrakeAndDriftArePlayable() {
        var state = KartState()
        repeat(120) { state = KartPhysics.step(state, 0f, 1f, 0f, false, 1f / 60) }
        assertEquals(0f, state.speed, 0f)
        repeat(180) { state = KartPhysics.step(state, 0f, 1f, 0f, false, 1f / 60) }
        assertTrue(state.speed > 10f)
        val grip = KartPhysics.step(state, .65f, 1f, 0f, false, .1f)
        val drift = KartPhysics.step(state, .65f, 1f, 0f, true, .1f)
        assertTrue(drift.drifting)
        assertTrue(abs(drift.yaw - state.yaw) > abs(grip.yaw - state.yaw))
        var braking = state
        repeat(90) { braking = KartPhysics.step(braking, 0f, 0f, 1f, false, 1f / 60) }
        assertTrue(braking.speed < state.speed * .2f)
        repeat(900) { state = KartPhysics.step(state, .8f, 1f, 0f, true, 1f / 60) }
        assertTrue(KartTrack.project(state.x, state.z).distance <= KartTrack.driveLimit + .01f)
        assertEquals(state, KartPhysics.step(state, Float.NaN, 1f, 0f, false, .1f))
    }

    @Test
    fun longTrackOrderedGatesAndSavedRacePreserveProgress() {
        assertTrue("Length ${KartTrack.length}", KartTrack.length > 550f)
        assertTrue(
            KartTrack.points.all {
                abs(it.x) + KartTrack.halfRoad < TourArea.OUTDOOR.halfWidth &&
                    abs(it.z) + KartTrack.halfRoad < TourArea.OUTDOOR.halfDepth
            }
        )
        var state = KartState(countdown = 0f, speed = 3f)
        // Staying at the start cannot earn a lap.
        repeat(15) {
            state = KartPhysics.step(state.copy(x = 0f, z = 84f), 0f, 0f, 0f, false, .01f)
        }
        assertEquals(0, state.laps)
        for (gate in 1..8) {
            val progress = (gate % 8) / 8f
            val p =
                KartTrack.points.minBy {
                    val t = KartTrack.project(it.x, it.z).progress
                    min(abs(t - progress), 1 - abs(t - progress))
                }
            state =
                KartPhysics.step(
                    state.copy(x = p.x, z = p.z, speed = 3f, vx = 0f, vz = 0f),
                    0f,
                    0f,
                    0f,
                    false,
                    .01f,
                )
        }
        assertEquals(1, state.laps)
        assertTrue(state.best > 0f)
        assertEquals(1, state.checkpoint)
        assertEquals(state.copy(drifting = false), KartState.restore(state.saved()))
        assertNull(KartState.restore("NaN"))
        assertNull(KartState.restore(state.copy(x = 999f).saved()))
    }

    @Test
    fun physicsCanDriveACompleteLapWithoutTeleporting() {
        var s = KartState(countdown = 0f)
        val samples = KartTrack.points.map { it to KartTrack.project(it.x, it.z).progress }
        repeat(18000) {
            if (s.laps > 0) return@repeat
            val projected = KartTrack.project(s.x, s.z)
            val target = (projected.progress + 6f / KartTrack.length) % 1f
            val point =
                samples
                    .minBy {
                        val t = it.second
                        min(abs(t - target), 1 - abs(t - target))
                    }
                    .first
            val yaw = atan2(point.x - s.x, -(point.z - s.z))
            val error = atan2(sin(yaw - s.yaw), cos(yaw - s.yaw))
            val steer = (error * 2.5f / max(.4f, s.speed * .058f)).coerceIn(-1f, 1f)
            val desired = if (abs(steer) > .6f) 9f else 13f
            s =
                KartPhysics.step(
                    s,
                    steer,
                    if (s.speed < desired) 1f else 0f,
                    if (s.speed > desired + 1f) .4f else 0f,
                    false,
                    1f / 60,
                )
        }
        assertTrue("Only reached gate ${s.checkpoint} at ${s.x},${s.z}", s.laps >= 1)
        assertTrue(s.best in 35f..180f)
    }
}
