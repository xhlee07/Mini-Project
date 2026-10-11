package com.ctfly.app

import kotlin.math.*

/** Shared by the visible road, driving boundaries, checkpoints and circuit map. Metres. */
object KartTrack {
    data class Point(val x: Float, val z: Float)

    data class Projection(val x: Float, val z: Float, val distance: Float, val progress: Float)

    const val halfRoad = 4.8f
    const val driveLimit = halfRoad + .1f
    private val knots =
        listOf(
            Point(0f, 84f),
            Point(-44f, 84f),
            Point(-66f, 60f),
            Point(-66f, 12f),
            Point(-47f, -20f),
            Point(-65f, -54f),
            Point(-49f, -83f),
            Point(-5f, -91f),
            Point(48f, -80f),
            Point(66f, -53f),
            Point(42f, -34f),
            Point(25f, -13f),
            Point(53f, 9f),
            Point(61f, 36f),
            Point(43f, 62f),
            Point(24f, 84f),
        )
    val points: List<Point> =
        knots.indices.flatMap { i ->
            val a = knots[(i + knots.size - 1) % knots.size]
            val b = knots[i]
            val c = knots[(i + 1) % knots.size]
            val d = knots[(i + 2) % knots.size]
            (0 until 24).map { step ->
                val t = step / 24f
                fun spline(v0: Float, v1: Float, v2: Float, v3: Float) =
                    .5f *
                        (2 * v1 +
                            (-v0 + v2) * t +
                            (2 * v0 - 5 * v1 + 4 * v2 - v3) * t * t +
                            (-v0 + 3 * v1 - 3 * v2 + v3) * t * t * t)
                Point(spline(a.x, b.x, c.x, d.x), spline(a.z, b.z, c.z, d.z))
            }
        }
    private val cumulative =
        FloatArray(points.size + 1).apply {
            for (i in points.indices) {
                val a = points[i]
                val b = points[(i + 1) % points.size]
                this[i + 1] = this[i] + hypot(b.x - a.x, b.z - a.z)
            }
        }
    val length
        get() = cumulative.last()

    val startYaw = atan2(points[1].x - points[0].x, -(points[1].z - points[0].z))

    fun gate(number: Int): Point {
        val progress = (number % 8) / 8f
        return points.indices
            .minBy {
                val t = cumulative[it] / length
                min(abs(t - progress), 1f - abs(t - progress))
            }
            .let { points[it] }
    }

    fun project(x: Float, z: Float): Projection {
        var best = Projection(points[0].x, points[0].z, Float.MAX_VALUE, 0f)
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            val dx = b.x - a.x
            val dz = b.z - a.z
            val t = ((x - a.x) * dx + (z - a.z) * dz).div(dx * dx + dz * dz).coerceIn(0f, 1f)
            val px = a.x + dx * t
            val pz = a.z + dz * t
            val distance = hypot(x - px, z - pz)
            if (distance < best.distance)
                best = Projection(px, pz, distance, (cumulative[i] + hypot(dx, dz) * t) / length)
        }
        return best
    }

    fun edge(i: Int, offset: Float): Point {
        val p = points[i % points.size]
        val before = points[(i + points.size - 1) % points.size]
        val after = points[(i + 1) % points.size]
        val len = hypot(after.x - before.x, after.z - before.z)
        return Point(
            p.x - (after.z - before.z) / len * offset,
            p.z + (after.x - before.x) / len * offset,
        )
    }
}

data class KartState(
    val x: Float = KartTrack.points[0].x,
    val z: Float = KartTrack.points[0].z,
    val yaw: Float = KartTrack.startYaw,
    val vx: Float = 0f,
    val vz: Float = 0f,
    val speed: Float = 0f,
    val countdown: Float = 3f,
    val elapsed: Float = 0f,
    val lapTime: Float = 0f,
    val best: Float = 0f,
    val laps: Int = 0,
    val checkpoint: Int = 1,
    val drifting: Boolean = false,
) {
    fun saved() =
        listOf(x, z, yaw, vx, vz, speed, countdown, elapsed, lapTime, best, laps, checkpoint)
            .joinToString(",")

    companion object {
        fun restore(value: String): KartState? =
            runCatching {
                    val v = value.split(',').map { it.toFloat() }
                    require(v.size == 12 && v.all { it.isFinite() })
                    require(KartTrack.project(v[0], v[1]).distance <= KartTrack.driveLimit + .01f)
                    require(abs(v[5]) <= 28f && abs(v[3]) <= 35f && abs(v[4]) <= 35f)
                    require(
                        v[6] in 0f..3f &&
                            v[7] >= 0 &&
                            v[8] >= 0 &&
                            v[9] >= 0 &&
                            v[10] >= 0 &&
                            v[11] in 1f..8f
                    )
                    KartState(
                        v[0],
                        v[1],
                        v[2],
                        v[3],
                        v[4],
                        v[5],
                        v[6],
                        v[7],
                        v[8],
                        v[9],
                        v[10].toInt(),
                        v[11].toInt(),
                    )
                }
                .getOrNull()
    }
}

object KartPhysics {
    fun step(
        state: KartState,
        steer: Float,
        throttle: Float,
        brake: Float,
        drift: Boolean,
        seconds: Float,
        reverse: Float = 0f,
    ): KartState {
        if (!listOf(steer, throttle, brake, seconds, reverse).all { it.isFinite() }) return state
        var s = state
        var remaining = seconds.coerceIn(0f, .5f)
        while (remaining > 0f) {
            val dt = min(remaining, 1f / 120f)
            remaining -= dt
            if (s.countdown > 0f) {
                s = s.copy(countdown = max(0f, s.countdown - dt))
                continue
            }
            val backing = reverse > .05f
            val sliding = !backing && (brake > .05f || drift) && s.speed > 5f && abs(steer) > .15f
            val drag = (.35f + .013f * s.speed * s.speed) * sign(s.speed)
            val drive = if (backing) -6.5f else throttle.coerceIn(0f, 1f) * 7.2f
            val braking = brake.coerceIn(0f, 1f) * (if (sliding) 1.2f else 14f) * sign(s.speed)
            var speed = (s.speed + (drive - drag - braking) * dt).coerceIn(-6f, 28f)
            if (!backing && throttle == 0f && s.speed * speed < 0f) speed = 0f
            if (!backing && throttle > 0f && s.speed < 0f && speed > 0f) speed = 0f
            // Kerbs remove a little speed over time, never .96 repeatedly per 1/120 s step.
            if (KartTrack.project(s.x, s.z).distance > KartTrack.halfRoad - .65f) {
                speed -= sign(speed) * max(0f, abs(speed) - 9f) * .4f * dt
            }
            val yaw =
                s.yaw + steer.coerceIn(-1f, 1f) * speed * .058f * (if (sliding) 1.6f else 1f) * dt
            val grip = 1 - exp(-(if (sliding) 1.35f else 10f) * dt)
            var vx = s.vx + (sin(yaw) * speed - s.vx) * grip
            var vz = s.vz + (-cos(yaw) * speed - s.vz) * grip
            var x = s.x + vx * dt
            var z = s.z + vz * dt
            val road = KartTrack.project(x, z)
            var newSpeed = speed
            val limit = KartTrack.driveLimit
            if (road.distance > limit) {
                val nx = (x - road.x) / road.distance
                val nz = (z - road.z) / road.distance
                x = road.x + nx * limit
                z = road.z + nz * limit
                val outward = max(0f, vx * nx + vz * nz)
                vx -= nx * outward
                vz -= nz * outward
                // Preserve enough steering authority to recover from a rail; reverse remains free.
                newSpeed = sign(speed) * min(abs(speed), max(3f, hypot(vx, vz)))
            }
            var checkpoint = s.checkpoint
            var laps = s.laps
            var best = s.best
            var lapTime = s.lapTime + dt
            // Ordered gates prevent reverse start-line crossings and shortcut lap farming.
            val gate = (checkpoint % 8) / 8f
            val progress = KartTrack.project(x, z).progress
            val delta = min(abs(progress - gate), 1f - abs(progress - gate))
            if (delta < .012f && newSpeed > 1f) {
                if (checkpoint == 8) {
                    laps++
                    best = if (best == 0f) lapTime else min(best, lapTime)
                    lapTime = 0f
                    checkpoint = 1
                } else checkpoint++
            }
            s =
                s.copy(
                    x = x,
                    z = z,
                    yaw = yaw,
                    vx = vx,
                    vz = vz,
                    speed = newSpeed,
                    elapsed = s.elapsed + dt,
                    lapTime = lapTime,
                    best = best,
                    laps = laps,
                    checkpoint = checkpoint,
                    drifting = sliding,
                )
        }
        return s
    }
}

/** Ballistic jump with a fixed floor: repeated presses cannot turn it into flight. */
class TourJump {
    var height = 0f
        private set

    private var velocity = 0f

    fun trigger() {
        if (height == 0f && velocity == 0f) velocity = 5f
    }

    fun reset() {
        height = 0f
        velocity = 0f
    }

    fun step(seconds: Float) {
        if (!seconds.isFinite()) return
        var remaining = seconds.coerceIn(0f, .5f)
        while (remaining > 0f) {
            val dt = min(remaining, 1f / 120f)
            remaining -= dt
            if (height > 0f || velocity > 0f) {
                height = max(0f, height + velocity * dt - 4.9f * dt * dt)
                velocity -= 9.8f * dt
                if (height == 0f) velocity = 0f
            }
        }
    }
}

/** One layout drives visible rooms, door hinges, navigation and collision on every floor. */
object WashroomDoors {
    const val z = -14.7f
    const val width = 1.18f
    val hinges = listOf(-9.57f, -8.17f, -6.77f)

    data class Room(val area: TourArea, val left: Float, val rowZ: Float) {
        val front
            get() = rowZ + 2.6f

        val back
            get() = rowZ - 2.15f

        val right
            get() = left + 7f

        val entranceX
            get() = left + 2.31f

        val outside
            get() = TourPose(entranceX, front + 0.75f, area = area)

        val washPose
            get() = TourPose(entranceX, rowZ + 1.4f, area = area)
    }

    val rooms =
        listOf(
            Room(TourArea.LOUNGE, -9.65f, z),
            Room(TourArea.GAMING, -3.5f, -29.7f),
            Room(TourArea.BILLIARDS, -3.5f, -21.7f),
            Room(TourArea.OUTDOOR, -41.5f, 104.7f),
        )

    data class Door(
        val index: Int,
        val area: TourArea,
        val x: Float,
        val z: Float,
        val label: String,
    ) {
        val centreX
            get() = x + width / 2

        fun obstacle(open: Boolean) =
            if (open) TourObstacle(x, z - width / 2, .075f, width)
            else TourObstacle(centreX, z, width, .075f)

        // Conservative swept volume while the leaf is turning. Nobody can walk through it.
        val sweep
            get() = TourObstacle(x + width / 2, z - width / 2, width, width)
    }

    val doors =
        rooms.flatMapIndexed { r, room ->
            (0..4).map { i ->
                Door(
                    r * 6 + i,
                    room.area,
                    room.left + .08f + i * 1.4f,
                    room.rowZ,
                    if (i < 3) "Toilet ${i + 1}" else "Shower ${i - 2}",
                )
            } +
                Door(
                    r * 6 + 5,
                    room.area,
                    room.entranceX - width / 2,
                    room.front,
                    "Washroom entrance",
                )
        }
    val validMask = (1 shl doors.size) - 1

    fun inArea(area: TourArea) = doors.filter { it.area == area }

    fun obstacle(index: Int, open: Boolean) = doors[index].obstacle(open)

    fun nearest(p: TourPose): Int? =
        inArea(p.area)
            .filter { hypot(p.x - it.centreX, p.z - it.z) < 1.9f }
            .minByOrNull {
                val dx = it.centreX - p.x
                val dz = it.z - p.z
                val distance = hypot(dx, dz)
                val facing = (dx * sin(p.yaw) - dz * cos(p.yaw)) / max(.01f, distance)
                distance + max(0f, -facing) * 1.2f
            }
            ?.index

    fun canToggle(index: Int, p: TourPose) =
        p.area == doors[index].area &&
            TourPlan.canStand(p.x, p.z, listOf(doors[index].sweep), p.area)

    fun canClose(index: Int, p: TourPose) = canToggle(index, p)
}
