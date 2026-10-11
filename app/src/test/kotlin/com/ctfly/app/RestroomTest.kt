package com.ctfly.app

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class RestroomTest {
    @Test
    fun everyFloorHasEnterableToiletsAndSeparateShowers() {
        assertEquals(TourArea.entries.toSet(), WashroomDoors.rooms.map { it.area }.toSet())
        assertEquals(24, WashroomDoors.doors.size)
        for (room in WashroomDoors.rooms) {
            val doors = WashroomDoors.inArea(room.area)
            assertEquals(3, doors.count { it.label.startsWith("Toilet") })
            assertEquals(2, doors.count { it.label.startsWith("Shower") })
            val world = TourWorld(room.area)
            val open = world.obstacles + doors.map { it.obstacle(true) }
            assertTrue(
                "Outside ${room.area}",
                TourPlan.canStand(room.outside.x, room.outside.z, open, room.area),
            )
            // Walk through the actual doorway and into every cubicle, including shower trays.
            fun walk(p: TourPose, x: Float, z: Float): TourPose {
                val dx = x - p.x
                val dz = p.z - z
                var pos = p.copy(yaw = 0f)
                val distance = hypot(dx, dz)
                repeat(ceil(distance / .045f).toInt()) {
                    pos = TourPlan.move(pos, dx / distance, dz / distance, .01f, open)
                }
                assertTrue(
                    "${room.area} cannot reach $x,$z from ${p.x},${p.z}: $pos",
                    hypot(pos.x - x, pos.z - z) < .09f,
                )
                return pos
            }
            var pos = walk(room.outside, room.entranceX, room.rowZ + .4f)
            for (door in doors.filter { it.label != "Washroom entrance" }) {
                // A narrow shared corridor connects all five stalls without crossing partitions.
                pos = walk(pos, door.centreX + .16f, room.rowZ + .4f)
                pos = walk(pos, door.centreX + .16f, room.rowZ - 1.48f)
                assertTrue(
                    "Close from inside ${door.label}",
                    WashroomDoors.canClose(door.index, pos),
                )
                pos = walk(pos, door.centreX + .16f, room.rowZ + .4f)
            }
        }
    }

    @Test
    fun doorsBlockWhenClosedAndOpenWithoutTrappingUsers() {
        for (door in WashroomDoors.doors) {
            val threshold = TourPose(door.centreX, door.z, area = door.area)
            assertFalse(
                TourPlan.canStand(threshold.x, threshold.z, listOf(door.obstacle(false)), door.area)
            )
            assertTrue(
                TourPlan.canStand(threshold.x, threshold.z, listOf(door.obstacle(true)), door.area)
            )
            assertFalse(WashroomDoors.canToggle(door.index, threshold))
            assertTrue(WashroomDoors.canToggle(door.index, threshold.copy(z = door.z + .75f)))
            assertTrue(WashroomDoors.canToggle(door.index, threshold.copy(z = door.z - 1.55f)))
            assertFalse(
                WashroomDoors.canToggle(
                    door.index,
                    threshold.copy(area = TourArea.entries.first { it != door.area }),
                )
            )
            assertEquals(door.index, WashroomDoors.nearest(threshold))
        }
        assertEquals(0xFFFFFF, WashroomDoors.validMask)
    }

    @Test
    fun relocatedPavilionClearsRoadRailsAndChaseCamera() {
        val room = WashroomDoors.rooms.single { it.area == TourArea.OUTDOOR }
        // Entire roof/canopy plus 5 m chase camera and curb/rail envelope.
        for (point in KartTrack.points) {
            val dx = max(0f, max(room.left - .6f - point.x, point.x - room.right - .6f))
            val dz = max(0f, max(room.back - .3f - point.z, point.z - room.front - 2.1f))
            assertTrue(
                "Building too near road at $point",
                hypot(dx, dz) > KartTrack.halfRoad + 1.25f + 5f,
            )
        }
        val world = TourWorld(TourArea.OUTDOOR)
        assertTrue(
            "Old solid box removed",
            TourPlan.canStand(-40f, 96f, world.obstacles, TourArea.OUTDOOR),
        )
    }
}
