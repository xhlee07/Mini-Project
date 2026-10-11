package com.ctfly.app

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class CampusWorldTest {
    @Test
    fun destinationsAndLiftsAreReachableOnEveryFloor() {
        for (area in TourArea.entries) {
            val world = TourWorld(area)
            val destinations =
                TourPlan.stops.filter { it.pose.area == area }.map { it.pose } +
                    TourPlan.connector(area) +
                    if (area == TourArea.GAMING)
                        PrivateRoomPlan.rooms.flatMap { listOf(it.doorPose, it.insidePose) }
                    else emptyList()
            for (p in destinations) assertTrue(
                "${area.name}: blocked ${p.x},${p.z}",
                TourPlan.canStand(p.x, p.z, world.obstacles, area),
            )
            val seen = mutableSetOf<Pair<Int, Int>>()
            val queue = java.util.ArrayDeque<Pair<Int, Int>>()
            val entry = TourPlan.entry(area).pose
            val start = round(entry.x * 4).toInt() to round(entry.z * 4).toInt()
            queue.add(start)
            seen.add(start)
            while (queue.isNotEmpty()) {
                val p = queue.removeFirst()
                for (d in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val n = p.first + d.first to p.second + d.second
                    if (
                        n !in seen &&
                            TourPlan.canStand(
                                n.first * .25f,
                                n.second * .25f,
                                world.obstacles,
                                area,
                            )
                    ) {
                        seen.add(n)
                        queue.add(n)
                    }
                }
            }
            for (p in destinations) assertTrue(
                "${area.name}: unreachable ${p.x},${p.z}",
                seen.any { abs(it.first * .25f - p.x) < .3f && abs(it.second * .25f - p.z) < .3f },
            )
        }
    }

    @Test
    fun movementStaysInsideFloorAndHandlesInvalidInput() {
        for (area in TourArea.entries) {
            val world = TourWorld(area)
            val edge = TourPose(area.halfWidth - .4f, 0f, area = area)
            assertTrue(TourPlan.move(edge, 1f, 0f, 1f, world.obstacles).x <= area.halfWidth - .35f)
            val start = TourPlan.entry(area).pose
            val moved = TourPlan.move(start, 1f, 1f, 1f, world.obstacles)
            assertTrue(hypot(moved.x - start.x, moved.z - start.z) <= 2.251f)
            assertEquals(start, TourPlan.move(start, Float.NaN, 0f, 1f, world.obstacles))
        }
    }

    @Test
    fun floorsContainTheirOwnFacilitiesWithExpandedCapacity() {
        val expected =
            mapOf(
                TourArea.GAMING to mapOf("PC" to 24, "VIPRoom" to 9, "VIP" to 21),
                TourArea.BILLIARDS to mapOf("Pool" to 8, "Snooker" to 4),
                TourArea.LOUNGE to
                    mapOf("Console" to 4, "Racing" to 4, "RacingVIP" to 4, "Arcade" to 6),
                TourArea.OUTDOOR to mapOf("GoKart" to 6),
            )
        for (area in TourArea.entries) assertEquals(
            area.name,
            expected[area],
            TourWorld(area).fixtures,
        )
    }

    @Test
    fun shippedMeshesMatchSourceGeometryAndCollision() {
        for (area in TourArea.entries) {
            val world = TourWorld(area)
            assertEquals(0, world.vertices.size % 36)
            assertTrue(world.vertices.all { it.isFinite() })
            val path =
                java.io.File("src/main/assets/${area.asset}").let {
                    if (it.exists()) it else java.io.File("../app/src/main/assets/${area.asset}")
                }
            val data =
                java.nio.ByteBuffer.wrap(path.readBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            assertEquals(0x43544632, data.int)
            assertEquals(2, data.int)
            assertEquals(world.obstacles.size, data.int)
            assertEquals(world.vertices.size, data.int)
            for (o in world.obstacles) {
                assertEquals(o.x, data.float, 0f)
                assertEquals(o.z, data.float, 0f)
                assertEquals(o.w, data.float, 0f)
                assertEquals(o.d, data.float, 0f)
            }
            for (i in 0 until world.vertices.size) if (world.vertices.data[i] != data.float)
                fail("${area.name}: stale float $i; run :app:bakeTourMesh")
            assertFalse(data.hasRemaining())
        }
    }
}
