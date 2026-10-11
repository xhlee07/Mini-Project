package com.ctfly.app

import kotlin.math.*

data class TourPose(
    val x: Float = 0f,
    val z: Float = 23f,
    val yaw: Float = 0f,
    val pitch: Float = -0.08f,
    val area: TourArea = TourArea.GAMING,
)

data class TourStop(val name: String, val pose: TourPose, val description: String)

data class TourObstacle(val x: Float, val z: Float, val w: Float, val d: Float)

object TourPlan {
    private fun stop(
        name: String,
        area: TourArea,
        x: Float,
        z: Float,
        yaw: Float,
        description: String,
    ) = TourStop(name, TourPose(x, z, yaw, -0.12f, area), description)

    val stops =
        listOf(
            stop(
                "Entrance",
                TourArea.GAMING,
                0f,
                23f,
                0f,
                "L1 · Esports floor. Take the lift to visit other levels.",
            ),
            stop(
                "Reception",
                TourArea.GAMING,
                -9.5f,
                23f,
                0f,
                "Check in with the crew at the front desk.",
            ),
            stop(
                "PC arena",
                TourArea.GAMING,
                0f,
                17f,
                -0.35f,
                "24 rigs on continuous shared esports benches with open aisles.",
            ),
            stop(
                "VIP room",
                TourArea.GAMING,
                0f,
                -0.8f,
                0f,
                "Nine private rooms with 1, 2 or 4 gaming PCs. Book the whole room in Play.",
            ),
            stop(
                "Pool lounge",
                TourArea.BILLIARDS,
                0.5f,
                7.2f,
                0.65f,
                "L2 · Eight pool tables. The games floor is downstairs.",
            ),
            stop(
                "Snooker hall",
                TourArea.BILLIARDS,
                0f,
                -6f,
                -0.5f,
                "Four full-size snooker tables with generous cue clearance.",
            ),
            stop(
                "Players' bar",
                TourArea.BILLIARDS,
                -8f,
                15.2f,
                0f,
                "A dedicated upstairs refreshment counter and waiting lounge.",
            ),
            stop(
                "Console",
                TourArea.LOUNGE,
                -7.5f,
                12.1f,
                0f,
                "G · Four enclosed console suites away from the PC floor.",
            ),
            stop(
                "Simulator lab",
                TourArea.LOUNGE,
                2f,
                -1.5f,
                0.6f,
                "Four cockpit rigs with wheels, pedals and displays.",
            ),
            stop(
                "Arcade",
                TourArea.LOUNGE,
                0f,
                13.6f,
                -2f,
                "Six arcade cabinets in the social wing.",
            ),
            stop(
                "Café",
                TourArea.LOUNGE,
                2.5f,
                11.6f,
                0.5f,
                "Coffee, snacks and window-side tables.",
            ),
            stop(
                "Washroom",
                TourArea.LOUNGE,
                -7.34f,
                -11.35f,
                0f,
                "Three private toilets, two separate showers and a wash area. Approach a door to open or close it.",
            ),
            stop(
                "Go-kart",
                TourArea.OUTDOOR,
                0f,
                108f,
                0f,
                "G · Ground-level paddock. Find the kart time-trial easter egg.",
            ),
            stop(
                "Trackside",
                TourArea.OUTDOOR,
                24f,
                99f,
                -0.65f,
                "View the circuit from the pedestrian path behind the barriers.",
            ),
        ) +
            listOf(
                stop(
                    "Solo room",
                    TourArea.GAMING,
                    0f,
                    -3.8f,
                    0f,
                    "Private single-PC room · RM15 / room / hr",
                ),
                stop(
                    "Duo room",
                    TourArea.GAMING,
                    0f,
                    -10.8f,
                    0f,
                    "Private two-PC room · RM28 / room / hr",
                ),
                stop(
                    "Squad room",
                    TourArea.GAMING,
                    0f,
                    -17.8f,
                    0f,
                    "Private four-PC room · RM52 / room / hr",
                ),
                stop(
                    "VIP simulator",
                    TourArea.LOUNGE,
                    10.8f,
                    -3f,
                    -1.57f,
                    "Private cockpit booths · RM35 / simulator / hr",
                ),
            ) + WashroomDoors.rooms.filter { it.area != TourArea.LOUNGE }.map {
                TourStop("Washroom", it.outside, "Three private toilets, two separate showers and a wash area. Approach a door to open or close it.")
            }

    fun entry(area: TourArea) = stops.first { it.pose.area == area }

    fun connector(area: TourArea) =
        if (area == TourArea.OUTDOOR) TourPose(0f, 111f, 0f, 0f, area)
        else TourPose(area.halfWidth - 3f, area.halfDepth - 5f, 0f, 0f, area)

    fun canStand(
        x: Float,
        z: Float,
        obstacles: List<TourObstacle>,
        area: TourArea = TourArea.GAMING,
    ): Boolean {
        val radius = 0.24f
        if (
            !x.isFinite() ||
                !z.isFinite() ||
                kotlin.math.abs(x) > area.halfWidth - 0.35f ||
                kotlin.math.abs(z) > area.halfDepth - 0.35f
        )
            return false
        return obstacles.none {
            abs(x - it.x) < it.w / 2 + radius && abs(z - it.z) < it.d / 2 + radius
        }
    }

    fun move(
        p: TourPose,
        strafe: Float,
        forward: Float,
        seconds: Float,
        obstacles: List<TourObstacle>,
        speed: Float = 4.5f,
    ): TourPose {
        if (!strafe.isFinite() || !forward.isFinite() || !seconds.isFinite() || !speed.isFinite())
            return p
        val len = max(1f, sqrt(strafe * strafe + forward * forward))
        // Small steps prevent faster walking/sprinting from tunnelling through thin walls.
        val distance = seconds.coerceIn(0f, 0.5f) * speed.coerceIn(0f, 8f)
        val steps = max(1, ceil(distance / 0.06f).toInt())
        val dx = (cos(p.yaw) * strafe + sin(p.yaw) * forward) / len * distance / steps
        val dz = (sin(p.yaw) * strafe - cos(p.yaw) * forward) / len * distance / steps
        var x = p.x
        var z = p.z
        repeat(steps) {
            if (canStand(x + dx, z, obstacles, p.area)) x += dx
            if (canStand(x, z + dz, obstacles, p.area)) z += dz
        }
        return p.copy(x = x, z = z)
    }
}

/** A single static triangle batch: positions, normals, albedo, atlas UVs and emissive amount. */
class TourVertices : Iterable<Float> {
    var data = FloatArray(262144)
        private set

    var size = 0
        private set

    fun add(value: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    operator fun set(index: Int, value: Float) {
        data[index] = value
    }

    override fun iterator(): Iterator<Float> =
        object : Iterator<Float> {
            private var index = 0

            override fun hasNext() = index < size

            override fun next() = data[index++]
        }
}

class TourWorld(val area: TourArea = TourArea.GAMING, buildScene: Boolean = true) {
    val fixtures = mutableMapOf<String, Int>()

    private fun record(kind: String) {
        fixtures[kind] = (fixtures[kind] ?: 0) + 1
    }

    val vertices = TourVertices()
    val obstacles = mutableListOf<TourObstacle>()
    private val charcoal = 0x242E38
    private val metal = 0x536571
    private val lime = 0xBAED68
    private val aqua = 0x65D9CD
    private val violet = 0xAA8AE5

    fun box(
        x: Float,
        y: Float,
        z: Float,
        w: Float,
        h: Float,
        d: Float,
        color: Int,
        tile: Int = 0,
        glow: Float = 0f,
        solid: Boolean = false,
    ) {
        if (solid) obstacles.add(TourObstacle(x, z, w, d))
        val l = x - w / 2
        val r = x + w / 2
        val b = y - h / 2
        val t = y + h / 2
        val f = z + d / 2
        val k = z - d / 2
        face(
            floatArrayOf(l, b, f, r, b, f, r, t, f, l, t, f),
            floatArrayOf(0f, 0f, 1f),
            color,
            tile,
            glow,
        )
        face(
            floatArrayOf(r, b, k, l, b, k, l, t, k, r, t, k),
            floatArrayOf(0f, 0f, -1f),
            color,
            tile,
            glow,
        )
        face(
            floatArrayOf(r, b, f, r, b, k, r, t, k, r, t, f),
            floatArrayOf(1f, 0f, 0f),
            color,
            tile,
            glow,
        )
        face(
            floatArrayOf(l, b, k, l, b, f, l, t, f, l, t, k),
            floatArrayOf(-1f, 0f, 0f),
            color,
            tile,
            glow,
        )
        face(
            floatArrayOf(l, t, f, r, t, f, r, t, k, l, t, k),
            floatArrayOf(0f, 1f, 0f),
            color,
            tile,
            glow,
        )
        face(
            floatArrayOf(l, b, k, r, b, k, r, b, f, l, b, f),
            floatArrayOf(0f, -1f, 0f),
            color,
            tile,
            glow,
        )
    }

    fun face(points: FloatArray, n: FloatArray, color: Int, tile: Int = 0, glow: Float = 0f) {
        val uv = floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f)
        for (i in intArrayOf(0, 1, 2, 0, 2, 3)) {
            for (j in 0..2) vertices.add(points[i * 3 + j])
            for (component in n) vertices.add(component)
            vertices.add(((color shr 16) and 255) / 255f)
            vertices.add(((color shr 8) and 255) / 255f)
            vertices.add((color and 255) / 255f)
            vertices.add((tile % 8 + (uv[i * 2] * 0.98f + 0.01f)) / 8f)
            vertices.add((tile / 8 + (uv[i * 2 + 1] * 0.98f + 0.01f)) / 8f)
            vertices.add(glow)
        }
    }

    private fun sign(x: Float, y: Float, z: Float, w: Float, h: Float, tile: Int) =
        box(x, y, z, w, h, 0.025f, 0xFFFFFF, tile, 0.65f)

    /** Smooth corner rails and upholstered cabinet edges. */
    private fun roundedBox(
        x: Float,
        y: Float,
        z: Float,
        w: Float,
        h: Float,
        d: Float,
        color: Int,
        tile: Int = 0,
        radius: Float = 0.06f,
        solid: Boolean = false,
    ) {
        if (solid) obstacles.add(TourObstacle(x, z, w, d))
        val r = min(radius, min(w, d) / 3)
        val points = mutableListOf<Pair<Float, Float>>()
        val normals = mutableListOf<Pair<Float, Float>>()
        for (c in 0..3) for (j in 0..5) {
            val a = (c * PI / 2 + j * PI / 10).toFloat()
            val cx = x + if (c == 0 || c == 3) w / 2 - r else -w / 2 + r
            val cz = z + if (c < 2) d / 2 - r else -d / 2 + r
            points.add(cx + cos(a) * r to cz + sin(a) * r)
            normals.add(cos(a) to sin(a))
        }
        for (i in points.indices) {
            val j = (i + 1) % points.size
            val a = points[i]
            val b = points[j]
            smoothFace(
                floatArrayOf(
                    a.first,
                    y - h / 2,
                    a.second,
                    b.first,
                    y - h / 2,
                    b.second,
                    b.first,
                    y + h / 2,
                    b.second,
                    a.first,
                    y + h / 2,
                    a.second,
                ),
                floatArrayOf(
                    normals[i].first,
                    0f,
                    normals[i].second,
                    normals[j].first,
                    0f,
                    normals[j].second,
                    normals[j].first,
                    0f,
                    normals[j].second,
                    normals[i].first,
                    0f,
                    normals[i].second,
                ),
                color,
                tile,
            )
            face(
                floatArrayOf(
                    x,
                    y + h / 2,
                    z,
                    a.first,
                    y + h / 2,
                    a.second,
                    b.first,
                    y + h / 2,
                    b.second,
                    x,
                    y + h / 2,
                    z,
                ),
                floatArrayOf(0f, 1f, 0f),
                color,
                tile,
            )
        }
    }

    /** Round tube between arbitrary endpoints: ducts, chair frames, cues and suspension wires. */
    private fun tube(a: FloatArray, b: FloatArray, radius: Float, color: Int, sides: Int = 16) {
        val axis = FloatArray(3) { b[it] - a[it] }
        val len = sqrt(axis.sumOf { (it * it).toDouble() }).toFloat()
        for (i in 0..2) axis[i] /= len
        val ref = if (abs(axis[1]) < 0.9f) floatArrayOf(0f, 1f, 0f) else floatArrayOf(1f, 0f, 0f)
        fun cross(u: FloatArray, v: FloatArray) =
            floatArrayOf(
                u[1] * v[2] - u[2] * v[1],
                u[2] * v[0] - u[0] * v[2],
                u[0] * v[1] - u[1] * v[0],
            )
        val u = cross(axis, ref)
        val ul = sqrt(u.sumOf { (it * it).toDouble() }).toFloat()
        for (i in 0..2) u[i] /= ul
        val v = cross(axis, u)
        for (s in 0 until sides) {
            fun normal(j: Int): FloatArray {
                val angle = j * 2f * PI.toFloat() / sides
                return FloatArray(3) { u[it] * cos(angle) + v[it] * sin(angle) }
            }
            val n = normal(s)
            val next = normal(s + 1)
            val p =
                FloatArray(3) { a[it] + n[it] * radius } +
                    FloatArray(3) { b[it] + n[it] * radius } +
                    FloatArray(3) { b[it] + next[it] * radius } +
                    FloatArray(3) { a[it] + next[it] * radius }
            smoothFace(p, n + n + next + next, color)
            face(
                a +
                    FloatArray(3) { a[it] + next[it] * radius } +
                    FloatArray(3) { a[it] + n[it] * radius } +
                    a,
                FloatArray(3) { -axis[it] },
                color,
            )
            face(
                b +
                    FloatArray(3) { b[it] + n[it] * radius } +
                    FloatArray(3) { b[it] + next[it] * radius } +
                    b,
                axis,
                color,
            )
        }
    }

    private fun smoothFace(points: FloatArray, normals: FloatArray, color: Int, tile: Int = 0) {
        val start = vertices.size
        face(points, floatArrayOf(0f, 1f, 0f), color, tile)
        for ((j, i) in intArrayOf(0, 1, 2, 0, 2, 3).withIndex()) for (k in 0..2) vertices[
            start + j * 12 + 3 + k] = normals[i * 3 + k]
    }

    private fun ring(x: Float, y: Float, z: Float, r: Float, inner: Float, color: Int) {
        for (i in 0 until 32) {
            val a = i * PI.toFloat() / 16
            val b = (i + 1) * PI.toFloat() / 16
            face(
                floatArrayOf(
                    x + r * cos(a),
                    y,
                    z + r * sin(a),
                    x + r * cos(b),
                    y,
                    z + r * sin(b),
                    x + inner * cos(b),
                    y,
                    z + inner * sin(b),
                    x + inner * cos(a),
                    y,
                    z + inner * sin(a),
                ),
                floatArrayOf(0f, 1f, 0f),
                color,
            )
        }
    }

    private fun pendant(x: Float, z: Float, w: Float, d: Float) {
        for (s in listOf(-1, 1)) {
            box(x + s * w / 2, 2.8f, z, 0.055f, 0.065f, d, 0x292B2D)
            box(x + s * w / 2, 2.761f, z, 0.035f, 0.012f, d, 0xFFF1D6, glow = 1f)
            box(x, 2.8f, z + s * d / 2, w, 0.065f, 0.055f, 0x292B2D)
            box(x, 2.761f, z + s * d / 2, w, 0.012f, 0.035f, 0xFFF1D6, glow = 1f)
            tube(
                floatArrayOf(x + s * w / 2, 2.83f, z + s * d / 2),
                floatArrayOf(x + s * w / 2, 3.74f, z + s * d / 2),
                0.006f,
                0x48494A,
                8,
            )
        }
    }

    private fun ellipsoid(
        x: Float,
        y: Float,
        z: Float,
        w: Float,
        h: Float,
        d: Float,
        color: Int,
        tile: Int = 0,
    ) {
        fun normal(a: Float, b: Float): FloatArray {
            val n = floatArrayOf(sin(a) * cos(b) / w, cos(a) / h, sin(a) * sin(b) / d)
            val len = sqrt(n.sumOf { (it * it).toDouble() }).toFloat()
            return FloatArray(3) { n[it] / len }
        }
        fun point(a: Float, b: Float) =
            floatArrayOf(
                x + sin(a) * cos(b) * w / 2,
                y + cos(a) * h / 2,
                z + sin(a) * sin(b) * d / 2,
            )
        val rows = if (max(w, max(h, d)) < 0.20f) 4 else 8
        val columns = rows * 2
        for (r in 0 until rows) for (j in 0 until columns) {
            val a = r * PI.toFloat() / rows
            val b = j * 2 * PI.toFloat() / columns
            val c = (r + 1) * PI.toFloat() / rows
            val e = (j + 1) * 2 * PI.toFloat() / columns
            smoothFace(
                point(a, b) + point(c, b) + point(c, e) + point(a, e),
                normal(a, b) + normal(c, b) + normal(c, e) + normal(a, e),
                color,
                tile,
            )
        }
    }

    private fun plant(x: Float, z: Float) {
        obstacles.add(TourObstacle(x, z, 0.48f, 0.48f))
        tube(floatArrayOf(x, 0.04f, z), floatArrayOf(x, 0.48f, z), 0.23f, 0x9C968C, 24)
        ring(x, 0.49f, z, 0.235f, 0.20f, 0xB5ACA0)
        ring(x, 0.482f, z, 0.2f, 0f, 0x332B23)
        // Layered tapered fronds with a central fold instead of solid oval foliage.
        for (i in 0 until 28) {
            val angle = i * 2.39996f
            val length = 0.48f + (i % 5) * 0.09f
            val height = 0.72f + (i % 7) * 0.09f
            tube(
                floatArrayOf(x, 0.46f, z),
                floatArrayOf(x + cos(angle) * 0.12f, height, z + sin(angle) * 0.12f),
                0.008f,
                0x53623D,
                6,
            )
            fun point(t: Float, side: Float): FloatArray {
                val reach = 0.12f + t * length
                val width = sin(t * PI.toFloat()) * 0.075f * side
                return floatArrayOf(
                    x + cos(angle) * reach - sin(angle) * width,
                    height + sin(t * 2.5f) * 0.24f - t * 0.13f - abs(side) * 0.024f,
                    z + sin(angle) * reach + cos(angle) * width,
                )
            }
            for (j in 0 until 5) for (side in listOf(-1f, 1f)) {
                val t = j / 5f
                val next = (j + 1) / 5f
                face(
                    point(t, 0f) + point(next, 0f) + point(next, side) + point(t, side),
                    floatArrayOf(-cos(angle) * 0.25f, 0.9f, -sin(angle) * 0.25f),
                    if (i % 3 == 0) 0x506E3E else 0x294B34,
                )
            }
        }
    }

    private fun chair(x: Float, z: Float, color: Int) {
        obstacles.add(TourObstacle(x, z, 0.64f, 0.64f))
        tube(floatArrayOf(x, 0.12f, z), floatArrayOf(x, 0.53f, z), 0.037f, 0x969EA2)
        for (i in 0..4) {
            val a = i * 2 * PI.toFloat() / 5
            val xx = x + cos(a) * 0.31f
            val zz = z + sin(a) * 0.31f
            tube(floatArrayOf(x, 0.15f, z), floatArrayOf(xx, 0.095f, zz), 0.025f, 0x44494C)
            ellipsoid(xx, 0.067f, zz, 0.095f, 0.11f, 0.08f, 0x191B1D)
        }
        ellipsoid(x, 0.57f, z, 0.6f, 0.18f, 0.57f, 0x33383B, 15)
        // Continuous upholstered back, soft bolsters and curved lumbar/head pads.
        // Smooth vertex normals remove the old sharp polygon outline at close range.
        bucketBack(x, z, color, false)

        ellipsoid(x, 1.39f, z + 0.105f, 0.25f, 0.13f, 0.07f, 0x15181B, 15)
        ellipsoid(x, 0.79f, z + 0.095f, 0.35f, 0.17f, 0.085f, 0x191D21, 15)
        for (side in listOf(-1, 1)) {
            ellipsoid(x + side * 0.23f, 1.01f, z + 0.18f, 0.09f, 0.68f, 0.16f, 0x44494C, 15)
            tube(
                floatArrayOf(x + side * 0.29f, 0.56f, z + 0.09f),
                floatArrayOf(x + side * 0.29f, 0.82f, z + 0.09f),
                0.019f,
                0x737B80,
            )
            ellipsoid(x + side * 0.3f, 0.83f, z, 0.105f, 0.065f, 0.36f, 0x222527, 15)
            box(x + side * 0.2f, 1.27f, z + 0.147f, 0.022f, 0.21f, 0.012f, color)
        }
    }

    private fun deskBank(x: Float, z: Float, seats: Int) {
        val width = seats * 1.6f
        roundedBox(x, 0.77f, z, width, 0.055f, 0.92f, 0x32353A, radius = 0.07f, solid = true)
        // Shared steel frame, open leg room, cable tray and warm under-desk light.
        for (s in listOf(-1, 1)) {
            val xx = x + s * (width / 2 - 0.12f)
            for (zz in listOf(z - 0.32f, z + 0.32f)) tube(
                floatArrayOf(xx, 0.03f, zz),
                floatArrayOf(xx, 0.74f, zz),
                0.025f,
                0x181D22,
            )
            tube(
                floatArrayOf(xx, 0.08f, z - 0.34f),
                floatArrayOf(xx, 0.08f, z + 0.34f),
                0.025f,
                0x181D22,
            )
        }
        box(x, 0.60f, z - 0.30f, width - 0.2f, 0.07f, 0.15f, 0x191F25)
        box(x, 0.725f, z - 0.35f, width - 0.2f, 0.012f, 0.014f, 0xCAD8E2, glow = 0.45f)
    }

    private fun pc(x: Float, z: Float, vip: Boolean = false) {
        record(if (vip) "VIP" else "PC")
        val accent = if (vip) violet else aqua
        // Thin curved display with an articulated VESA arm, rather than a block on a post.
        val mx = x - 0.12f
        for (i in 0 until 28) {
            val left = -0.48f + i * 0.96f / 28
            val right = -0.48f + (i + 1) * 0.96f / 28
            fun depth(xx: Float) = z - 0.20f + xx * xx * 0.21f
            val normal = floatArrayOf(-(left + right) * 0.21f, 0f, 1f)
            val nl = sqrt(normal[0] * normal[0] + 1)
            normal[0] /= nl
            normal[2] /= nl
            face(
                floatArrayOf(
                    mx + left,
                    0.985f,
                    depth(left),
                    mx + right,
                    0.985f,
                    depth(right),
                    mx + right,
                    1.545f,
                    depth(right),
                    mx + left,
                    1.545f,
                    depth(left),
                ),
                normal,
                0x12161A,
            )
            // UVs cover one complete screen across all strips.
            val begin = vertices.size
            face(
                floatArrayOf(
                    mx + left,
                    1.003f,
                    depth(left) + 0.006f,
                    mx + right,
                    1.003f,
                    depth(right) + 0.006f,
                    mx + right,
                    1.526f,
                    depth(right) + 0.006f,
                    mx + left,
                    1.526f,
                    depth(left) + 0.006f,
                ),
                normal,
                0xFFFFFF,
                3,
                0.42f,
            )
            for (v in 0 until 6) {
                val corner = intArrayOf(0, 1, 2, 0, 2, 3)[v]
                val u = (i + if (corner == 1 || corner == 2) 1f else 0f) / 28
                vertices[begin + v * 12 + 9] = (3 + (u * 0.98f + 0.01f)) / 8f
            }
            face(
                floatArrayOf(
                    mx + right,
                    0.985f,
                    depth(right) - 0.032f,
                    mx + left,
                    0.985f,
                    depth(left) - 0.032f,
                    mx + left,
                    1.545f,
                    depth(left) - 0.032f,
                    mx + right,
                    1.545f,
                    depth(right) - 0.032f,
                ),
                floatArrayOf(0f, 0f, -1f),
                0x22272B,
            )
            box(
                mx + (left + right) / 2,
                1.549f,
                depth((left + right) / 2) - 0.012f,
                0.96f / 28,
                0.009f,
                0.04f,
                0x14191D,
            )
        }
        tube(
            floatArrayOf(mx + 0.21f, 0.80f, z - 0.34f),
            floatArrayOf(mx + 0.21f, 1.08f, z - 0.34f),
            0.022f,
            0x3F484F,
        )
        tube(
            floatArrayOf(mx + 0.21f, 1.08f, z - 0.34f),
            floatArrayOf(mx, 1.20f, z - 0.25f),
            0.022f,
            0x3F484F,
        )
        box(mx, 1.20f, z - 0.24f, 0.10f, 0.12f, 0.018f, 0x4A5257)
        roundedBox(x - 0.02f, 0.804f, z + 0.16f, 1.01f, 0.008f, 0.42f, 0x1C2229, radius = 0.04f)
        roundedBox(x - 0.18f, 0.820f, z + 0.16f, 0.56f, 0.027f, 0.185f, 0x171E24, radius = 0.018f)
        for (i in 0..13) for (j in 0..3) box(
            x - 0.435f + i * 0.039f,
            0.836f,
            z + 0.095f + j * 0.038f,
            0.029f,
            0.008f,
            0.026f,
            0x66717B,
        )
        box(x - 0.18f, 0.812f, z + 0.25f, 0.53f, 0.006f, 0.006f, accent, glow = 0.42f)
        ellipsoid(x + 0.37f, 0.83f, z + 0.19f, 0.082f, 0.044f, 0.135f, 0xB4BABD)
        tube(
            floatArrayOf(x + 0.37f, 0.846f, z + 0.157f),
            floatArrayOf(x + 0.37f, 0.848f, z + 0.178f),
            0.008f,
            0x30353A,
            8,
        )
        roundedBox(x + 0.64f, 1.04f, z - 0.17f, 0.22f, 0.47f, 0.42f, 0x1C242C, radius = 0.02f)
        for (i in 0..2) {
            ellipsoid(x + 0.64f, 0.88f + i * 0.14f, z + 0.046f, 0.108f, 0.108f, 0.008f, 0x12191E)
            for (j in 0 until 24) {
                val a = j * PI.toFloat() / 12
                val b = (j + 1) * PI.toFloat() / 12
                fun point(angle: Float, radius: Float) =
                    floatArrayOf(
                        x + 0.64f + cos(angle) * radius,
                        0.88f + i * 0.14f + sin(angle) * radius,
                        z + 0.053f,
                    )
                face(
                    point(a, 0.039f) + point(a, 0.05f) + point(b, 0.05f) + point(b, 0.039f),
                    floatArrayOf(0f, 0f, 1f),
                    accent,
                    glow = 0.35f,
                )
            }
        }
        // Paired light bars, headset on a hook, visible tidy cabling and a desk cup.
        for (side in listOf(-1, 1)) {
            val xx = x + side * 0.53f
            box(xx, 1.13f, z - 0.27f, 0.023f, 0.54f, 0.027f, 0xC6D6E5, glow = 0.6f)
            roundedBox(xx, 0.812f, z - 0.27f, 0.13f, 0.018f, 0.12f, 0x222A30, radius = 0.02f)
        }
        tube(
            floatArrayOf(x - 0.70f, 0.74f, z + 0.12f),
            floatArrayOf(x - 0.70f, 0.74f, z + 0.27f),
            0.016f,
            0x141B22,
        )
        for (side in listOf(-1, 1)) ellipsoid(
            x - 0.70f + side * 0.077f,
            0.57f,
            z + 0.26f,
            0.065f,
            0.14f,
            0.07f,
            0x22282D,
            15,
        )
        for (i in 0 until 12) {
            val a = i * PI.toFloat() / 12
            val b = (i + 1) * PI.toFloat() / 12
            tube(
                floatArrayOf(x - 0.70f + cos(a) * 0.09f, 0.57f + sin(a) * 0.12f, z + 0.26f),
                floatArrayOf(x - 0.70f + cos(b) * 0.09f, 0.57f + sin(b) * 0.12f, z + 0.26f),
                0.014f,
                0x38424A,
                8,
            )
        }
        tube(
            floatArrayOf(mx, 1.15f, z - 0.29f),
            floatArrayOf(mx, 0.62f, z - 0.32f),
            0.006f,
            0x10171E,
            8,
        )
        tube(
            floatArrayOf(x + 0.54f, 0.81f, z + 0.20f),
            floatArrayOf(x + 0.54f, 0.89f, z + 0.20f),
            0.035f,
            0xDDD3BC,
            12,
        )
        ring(x + 0.54f, 0.897f, z + 0.20f, 0.035f, 0.027f, 0xE8DFC9)
        chair(x - 0.09f, z + 1.02f, accent)
    }

    private fun pool(x: Float, z: Float, snooker: Boolean) {
        record(if (snooker) "Snooker" else "Pool")
        val w = if (snooker) 2.5f else 2f
        val d = if (snooker) 4f else 3.1f
        val cloth = if (snooker) 0x38867A else 0x3895AF
        roundedBox(x, 0.69f, z, w - 0.06f, 0.27f, d - 0.06f, 0xB9A48C, 2, 0.1f, solid = true)
        roundedBox(x, 0.88f, z, w, 0.075f, d, 0xBFC4C5, 21, 0.1f)
        box(x, 0.548f, z, w - 0.12f, 0.045f, d - 0.12f, 0x272B2C)
        roundedBox(x, 0.91f, z, w - 0.28f, 0.04f, d - 0.28f, cloth, 14, 0.04f)
        for (side in listOf(-1, 1)) {
            roundedBox(
                x + side * (w / 2 - 0.08f),
                0.89f,
                z,
                0.16f,
                0.095f,
                d - 0.13f,
                0xBFC4C5,
                21,
                0.035f,
            )
            roundedBox(
                x,
                0.89f,
                z + side * (d / 2 - 0.08f),
                w - 0.13f,
                0.095f,
                0.16f,
                0xBFC4C5,
                21,
                0.035f,
            )
            box(x, 0.904f, z + side * (d / 2 - 0.17f), w - 0.42f, 0.075f, 0.10f, cloth, 14)
            for (half in listOf(-1, 1)) {
                box(
                    x + side * (w / 2 - 0.17f),
                    0.904f,
                    z + half * d / 4,
                    0.10f,
                    0.075f,
                    d / 2 - 0.24f,
                    cloth,
                    14,
                )
                tube(
                    floatArrayOf(x + side * (w / 2 - 0.29f), 0.1f, z + half * (d / 2 - 0.35f)),
                    floatArrayOf(x + side * (w / 2 - 0.35f), 0.62f, z + half * (d / 2 - 0.4f)),
                    0.125f,
                    0x45494A,
                )
                ring(
                    x + side * (w / 2 - 0.29f),
                    0.085f,
                    z + half * (d / 2 - 0.35f),
                    0.16f,
                    0f,
                    0x1D2021,
                )
            }
            for (j in -2..2) if (j != 0) {
                ellipsoid(
                    x + side * (w / 2 - 0.06f),
                    0.941f,
                    z + j * d / 6,
                    0.018f,
                    0.008f,
                    0.03f,
                    0xF4ECDD,
                )
            }
            for (sz in listOf(-1, 0, 1)) {
                val px = x + side * (w / 2 - 0.12f)
                val pz = z + sz * (d / 2 - 0.12f)
                ring(px, 0.943f, pz, 0.119f, 0.075f, 0x373C3D)
                ring(px, 0.938f, pz, 0.076f, 0f, 0x06090B)
                tube(floatArrayOf(px, 0.78f, pz), floatArrayOf(px, 0.932f, pz), 0.073f, 0x101517)
            }
        }
        val colors =
            intArrayOf(
                0xE4B62F,
                0x2962B8,
                0xCE4635,
                0x713A8D,
                0xD78630,
                0x2F7455,
                0x793B33,
                0x161B20,
            )
        for (i in 0..4) for (j in 0..i) ellipsoid(
            x + (j - i * 0.5f) * 0.068f,
            0.960f,
            z - 0.64f + i * 0.059f,
            0.058f,
            0.058f,
            0.058f,
            colors[(i + j) % 8],
        )
        ellipsoid(x + 0.24f, 0.960f, z + 0.7f, 0.058f, 0.058f, 0.058f, 0xF9F4E8)
        tube(
            floatArrayOf(x - 0.48f, 0.975f, z + 0.9f),
            floatArrayOf(x + 0.24f, 0.975f, z - 0.6f),
            0.009f,
            0xDCC79B,
            10,
        )
        tube(
            floatArrayOf(x - 0.48f, 0.975f, z + 0.9f),
            floatArrayOf(x - 0.26f, 0.975f, z + 0.44f),
            0.013f,
            0x4C3023,
            10,
        )
        box(x + w / 2 - 0.07f, 0.965f, z + 0.3f, 0.027f, 0.026f, 0.027f, 0x4488BC)
        pendant(x, z, w + 0.15f, d - 0.3f)
    }

    private fun floor(hw: Float, hd: Float, color: Int, tile: Int = 1) {
        // One continuous surface. Subpixel geometric gaps used to shimmer like dirty pixels.
        // Tile joints are now filtered in the shader using the projected pixel footprint.
        box(0f, -0.035f, 0f, hw * 2, 0.07f, hd * 2, color, tile)
    }

    private fun shell(color: Int = 0x34373B) {
        val w = area.halfWidth
        val d = area.halfDepth
        floor(w, d, if (area == TourArea.GAMING) 0xBCB0A1 else 0xC1BBAF, 1)
        box(0f, 3.9f, 0f, w * 2, 0.14f, d * 2, 0x161A1F)
        box(-w, 1.9f, 0f, 0.16f, 3.8f, d * 2, color, solid = true)
        box(0f, 1.9f, -d, w * 2, 3.8f, 0.16f, color, solid = true)
        box(0f, 1.9f, d, w * 2, 3.8f, 0.16f, color, solid = true)
        box(w, 0.42f, 0f, 0.16f, 0.84f, d * 2, color, solid = true)
        box(w, 3.6f, 0f, 0.16f, 0.6f, d * 2, color)
        box(w + 0.08f, 2f, 0f, 0.025f, 2.4f, d * 2, 0xA7B6CE, 12, 0.22f)
        for (z in -d.toInt()..d.toInt() step 3) box(
            w - 0.07f,
            2f,
            z.toFloat(),
            0.09f,
            2.6f,
            0.08f,
            0x272E33,
        )
        box(w - 0.1f, 2f, 0f, 0.1f, 0.055f, d * 2, 0x272E33)
        for (x in listOf(-w + 1.1f, w - 1.1f)) {
            tube(
                floatArrayOf(x, 3.56f, -d + 0.2f),
                floatArrayOf(x, 3.56f, d - 0.2f),
                0.14f,
                0x79848B,
                20,
            )
            for (z in -d.toInt() + 1 until d.toInt() step 2) tube(
                floatArrayOf(x, 3.56f, z - 0.014f),
                floatArrayOf(x, 3.56f, z + 0.014f),
                0.147f,
                0xABB0B4,
                20,
            )
        }
        for (z in -d.toInt() + 2 until d.toInt() step 4) box(
            0f,
            3.74f,
            z.toFloat(),
            w * 2,
            0.12f,
            0.095f,
            0x0C1117,
        )
        box(-w + 0.1f, 0.12f, 0f, 0.025f, 0.03f, d * 2 - 0.4f, 0x487BAA, glow = 0.8f)
        box(w - 0.1f, 0.12f, 0f, 0.025f, 0.03f, d * 2 - 0.4f, 0x487BAA, glow = 0.8f)
        val title =
            when (area) {
                TourArea.GAMING -> 13
                TourArea.BILLIARDS -> 6
                else -> 16
            }
        sign(0f, 2.58f, -d + 0.1f, 4.3f, 0.62f, title)
    }

    private fun lift() {
        val p = TourPlan.connector(area)
        val x = p.x
        val z = area.halfDepth - 2f
        box(x, 1.65f, z, 2.65f, 3.3f, 0.2f, 0x121C25, solid = true)
        box(x, 1.5f, z + 0.115f, 1.9f, 2.9f, 0.06f, 0xC3C7C8, 21)
        box(x, 1.5f, z + 0.151f, 0.018f, 2.9f, 0.005f, 0x252C33)
        sign(x, 3.12f, z + 0.16f, 1.5f, 0.33f, 9)
        box(x + 1.14f, 1.17f, z + 0.13f, 0.13f, 0.23f, 0.055f, 0x4B555E)
        ellipsoid(x + 1.14f, 1.18f, z + 0.167f, 0.04f, 0.04f, 0.012f, 0xA9DDEA)
    }

    private fun reception(x: Float, z: Float, w: Float = 4.8f) {
        box(x, 0.55f, z, w, 1.1f, 1.05f, 0xA9947A, 2, solid = true)
        box(x, 1.125f, z, w + 0.15f, 0.08f, 1.14f, 0xBAB7AE, 1)
        for (i in 0 until (w / 0.10f).toInt()) box(
            x - w / 2 + i * 0.1f,
            0.55f,
            z + 0.535f,
            0.025f,
            0.98f,
            0.025f,
            0x342722,
        )
        box(x, 0.08f, z + 0.555f, w - 0.1f, 0.018f, 0.018f, 0xE7C791, glow = 0.8f)
        sign(x, 0.64f, z + 0.57f, 1.5f, 0.47f, 4)
        box(x + 0.8f, 1.44f, z - 0.1f, 0.62f, 0.41f, 0.045f, 0x111519)
        sign(x + 0.8f, 1.44f, z - 0.069f, 0.57f, 0.35f, 3)
        tube(
            floatArrayOf(x + 0.8f, 1.15f, z - 0.1f),
            floatArrayOf(x + 0.8f, 1.34f, z - 0.1f),
            0.025f,
            0x4D555C,
        )
        box(x - 0.8f, 1.21f, z + 0.12f, 0.18f, 0.13f, 0.26f, 0x1F252B)
    }

    private fun bench(x: Float, z: Float, w: Float = 2.5f) {
        box(x, 0.23f, z, w, 0.4f, 0.8f, 0x242A31, solid = true)
        ellipsoid(x, 0.48f, z, w, 0.22f, 0.78f, 0x575D63, 15)
        box(x, 0.84f, z + 0.32f, w, 0.7f, 0.18f, 0x424952, 15)
        for (a in listOf(-1, 1)) box(
            x + a * (w / 2 - 0.1f),
            0.6f,
            z,
            0.16f,
            0.55f,
            0.9f,
            0x292F36,
            15,
        )
        for (i in -1..1) box(x + i * w / 3, 0.48f, z - 0.12f, 0.015f, 0.01f, 0.35f, 0x242A31)
    }

    private fun washroom() {
        val room = WashroomDoors.rooms.first { it.area == area }
        val l = room.left
        val r = room.right
        val row = room.rowZ
        val front = room.front
        val back = room.back
        val centre = (l + r) / 2
        val wall = 0xB7B2A5
        // Solid enclosure, a real entrance and a separate front wash corridor.
        for (x in listOf(l - 0.1f, r + 0.1f)) box(x, 1.5f, (front + back) / 2,
            0.16f, 3f, front - back + 0.16f, wall, 1, solid = true)
        box(centre, 1.5f, back, 7.2f, 3f, 0.16f, wall, 1, solid = true)
        val entry = room.entranceX
        for ((x1, x2) in listOf(l - 0.18f to entry - 0.68f, entry + 0.68f to r + 0.18f))
            box((x1 + x2) / 2, 1.5f, front, x2 - x1, 3f, 0.16f, wall, 1, solid = true)
        box(entry, 2.64f, front, 1.36f, 0.72f, 0.16f, wall, 1)
        box(centre, 3.05f, (front + back) / 2, 7.4f, 0.12f, 4.95f, 0xBBB6AD)
        floorPatch(centre, (front + back) / 2, 7.15f, 4.75f, 0xA8A292, 1)
        sign(entry, 2.75f, front + 0.091f, 1.2f, 0.3f, 7)
        // Timber fascia, satin trims and a sheltered, step-free outdoor doorway.
        if (area == TourArea.OUTDOOR) {
            box(centre, 3.1f, front + 0.6f, 8f, 0.16f, 1.5f, 0x52615D)
            for (x in listOf(l - 0.38f, r + 0.38f)) {
                box(x, 1.5f, front + 1.05f, 0.09f, 3f, 0.09f, 0x536663, 21, solid = true)
                plant(x, front + 1.7f)
            }
            for (i in 0..22) box(l + i * 0.31f, 2.29f, front + 0.1f,
                0.16f, 0.55f, 0.035f, 0xA08D72, 2)
        }
        for (x in listOf(l + 0.14f, r - 0.14f)) box(x, 0.12f, (front + back) / 2,
            0.02f, 0.18f, 4.65f, 0x737A73)
        // Two sculpted basins, taps, lit mirror, soap dispenser and a towel rail.
        roundedBox(l + 0.38f, 0.86f, row + 1.65f, 0.72f, 0.12f, 1.5f,
            0xDFDDD3, radius = 0.08f, solid = true)
        for (z in listOf(row + 1.1f, row + 1.9f)) {
            ring(l + 0.38f, 0.955f, z, 0.27f, 0.19f, 0xF1EDE3)
            ellipsoid(l + 0.38f, 0.916f, z, 0.4f, 0.065f, 0.4f, 0x82918C)
            tube(floatArrayOf(l + 0.1f, 0.95f, z), floatArrayOf(l + 0.1f, 1.15f, z), 0.019f, 0xC9CFCA, 21)
            tube(floatArrayOf(l + 0.1f, 1.15f, z), floatArrayOf(l + 0.25f, 1.15f, z), 0.019f, 0xC9CFCA, 21)
        }
        roundedBox(l + 0.012f, 1.72f, row + 1.5f, 0.04f, 1.1f, 1.8f, 0x9CAFAE, 21, radius = 0.035f)
        for (y in listOf(1.14f, 2.3f)) box(l + 0.05f, y, row + 1.5f, 0.025f, 0.025f, 1.8f, 0xF9E8CA, glow = 0.8f)
        roundedBox(l + 0.05f, 1.3f, row + 2.25f, 0.11f, 0.3f, 0.14f, 0x7A807B, 21, radius = 0.015f)
        for (i in 0..5) box(l + i * 1.4f, 1.16f, row - 1.05f,
            0.10f, 2.16f, 2.12f, 0x999487, 1, solid = true)
        for (i in 0..4) {
            val x = l + 0.7f + i * 1.4f
            for (side in listOf(-1, 1)) box(x + side * 0.66f, 1.16f, row,
                0.10f, 2.16f, 0.10f, 0xD4CFC3, solid = true)
            box(x, 2.26f, row, 1.4f, 0.06f, 0.10f, 0xD4CFC3)
            box(x, 2.48f, row, 0.92f, 0.28f, 0.06f, 0x35423F)
            sign(x, 2.48f, row + 0.036f, 0.88f, 0.24f, if (i < 3) 36 else 37)
            // Individual ceiling light and air vent. Small details give a human sense of scale.
            box(x, 2.98f, row - 1.1f, 0.7f, 0.025f, 0.5f, 0xF3EBDC, glow = 0.65f)
            box(x, 2.65f, back + 0.09f, 0.48f, 0.24f, 0.025f, 0x727976)
            for (v in 0..4) box(x, 2.57f + v * 0.04f, back + 0.11f, 0.42f, 0.012f, 0.02f, 0xBFC4BD)
            if (i < 3) {
                ellipsoid(x, 0.28f, row - 1.33f, 0.53f, 0.51f, 0.75f, 0xEFEDE3)
                roundedBox(x, 0.53f, row - 1.88f, 0.5f, 0.67f, 0.19f,
                    0xEFEDE3, radius = 0.05f, solid = true)
                ring(x, 0.552f, row - 1.28f, 0.21f, 0.13f, 0xEFEDE3)
                ring(x, 0.54f, row - 1.28f, 0.13f, 0f, 0x344852)
                box(x, 0.87f, row - 1.88f, 0.09f, 0.008f, 0.045f, 0x8F9C9A, 21)
                tube(floatArrayOf(x + 0.47f, 0.70f, row - 1.34f),
                    floatArrayOf(x + 0.47f, 0.70f, row - 1.18f), 0.047f, 0xECEAE1, 12)
            } else {
                // Fully enclosed shower, flush tray, drain, rain head, mixer and towel hook.
                roundedBox(x, 0.04f, row - 1.05f, 1.22f, 0.025f, 1.94f,
                    0xDAD9CF, radius = 0.04f)
                box(x, 0.055f, row - 1.6f, 0.36f, 0.012f, 0.12f, 0x626F70, 21)
                for (slot in 0..5) box(x - 0.15f + slot * 0.06f, 0.062f, row - 1.6f,
                    0.012f, 0.006f, 0.10f, 0x202D33)
                tube(floatArrayOf(x, 1f, back + 0.16f), floatArrayOf(x, 2.35f, back + 0.16f), 0.018f, 0xB6C4C4, 21)
                tube(floatArrayOf(x, 2.35f, back + 0.16f), floatArrayOf(x, 2.35f, row - 1.35f), 0.018f, 0xB6C4C4, 21)
                roundedBox(x, 2.33f, row - 1.35f, 0.32f, 0.035f, 0.32f, 0xB6C4C4, 21, radius = 0.035f)
                for (a in -2..2) for (b in -2..2) ellipsoid(x + a * 0.05f, 2.31f,
                    row - 1.35f + b * 0.05f, 0.008f, 0.005f, 0.008f, 0x35434A)
                box(x, 1.12f, back + 0.13f, 0.23f, 0.13f, 0.06f, 0xBAC7C4, 21)
                box(x + 0.35f, 1.3f, back + 0.15f, 0.22f, 0.035f, 0.18f, 0x949D94, 21)
                roundedBox(x + 0.35f, 1.39f, back + 0.16f, 0.07f, 0.16f, 0.06f, 0xB8B7A2, radius = 0.015f)
                tube(floatArrayOf(x + 0.6f, 1.73f, row - 0.55f),
                    floatArrayOf(x + 0.48f, 1.73f, row - 0.55f), 0.018f, 0xB6C4C4, 21)
            }
        }
        box(centre, 2.96f, row + 1.4f, 5f, 0.02f, 0.24f, 0xF3E2C5, glow = 0.7f)
        bench(r - 0.7f, row + 1.85f, 1.1f)
    }

    /** Local hinge mesh. Renderer rotates it around the matching world-space hinge. */
    fun cubicleDoor() {
        roundedBox(
            WashroomDoors.width / 2,
            1.15f,
            0f,
            WashroomDoors.width,
            2.08f,
            0.055f,
            0xBCB09C,
            35,
            radius = 0.012f,
        )
        box(WashroomDoors.width - 0.13f, 1.08f, 0.045f, 0.035f, 0.17f, 0.033f, 0xABB1B4, 21)
        tube(
            floatArrayOf(WashroomDoors.width - 0.13f, 1.11f, 0.075f),
            floatArrayOf(WashroomDoors.width - 0.27f, 1.11f, 0.075f),
            0.015f,
            0xBDC4C7,
        )
        box(WashroomDoors.width - 0.13f, 1.08f, -0.045f, 0.035f, 0.17f, 0.033f, 0xABB1B4, 21)
        tube(
            floatArrayOf(WashroomDoors.width - 0.13f, 1.11f, -0.075f),
            floatArrayOf(WashroomDoors.width - 0.27f, 1.11f, -0.075f),
            0.015f,
            0xBDC4C7,
        )
        ellipsoid(WashroomDoors.width - 0.13f, 1.26f, 0.038f, 0.035f, 0.035f, 0.008f, 0x93AE9A)
    }

    private fun console(x: Float, z: Float) {
        record("Console")
        bench(x, z, 3.1f)
        box(x, 0.4f, z - 1.55f, 1.5f, 0.09f, 0.7f, 0x6E5744, 2, solid = true)
        for (a in listOf(-1, 1)) {
            ellipsoid(x + a * 0.36f, 0.49f, z - 1.55f, 0.19f, 0.07f, 0.14f, 0x11171C)
            tube(
                floatArrayOf(x + a * 0.36f, 0.4f, z - 1.8f),
                floatArrayOf(x + a * 0.36f, 0.03f, z - 1.8f),
                0.025f,
                0x222932,
            )
        }
        box(x, 1.55f, z - 2.45f, 3f, 1.67f, 0.06f, 0x090D12)
        sign(x, 1.55f, z - 2.407f, 2.88f, 1.55f, 3)
        box(x + 0.95f, 0.53f, z - 2.25f, 0.14f, 0.66f, 0.4f, 0xD2D7D7)
        box(x + 0.95f, 0.53f, z - 2.23f, 0.03f, 0.68f, 0.4f, 0x101925)
        box(x, 1.5f, z - 2.62f, 6f, 3f, 0.12f, 0x29313E, solid = true)
        for (side in listOf(-1, 1)) box(
            x + side * 3f,
            1.5f,
            z - 0.2f,
            0.12f,
            3f,
            5f,
            0x303947,
            solid = true,
        )
        box(x, 3.01f, z - 0.2f, 6f, 0.06f, 5f, 0x18222F)
        sign(x, 2.73f, z + 0.73f, 2.1f, 0.35f, 16)
        for (a in listOf(-1, 1)) box(
            x + a * 2.85f,
            1.5f,
            z + 0.6f,
            0.025f,
            2.8f,
            0.025f,
            0x8867D0,
            glow = 0.65f,
        )
    }

    private fun wheel(x: Float, y: Float, z: Float) {
        val radius = 0.16f
        for (i in 0 until 24) {
            val a = i * PI.toFloat() / 12
            val b = (i + 1) * PI.toFloat() / 12
            tube(
                floatArrayOf(x + cos(a) * radius, y + sin(a) * radius, z),
                floatArrayOf(x + cos(b) * radius, y + sin(b) * radius, z),
                0.016f,
                0x1B2228,
                6,
            )
        }
        for (a in listOf(-1, 1)) tube(
            floatArrayOf(x, y, z),
            floatArrayOf(x + a * 0.12f, y, z),
            0.012f,
            0x9AABB2,
            8,
        )
        tube(floatArrayOf(x, y, z), floatArrayOf(x, y - 0.12f, z), 0.012f, 0x9AABB2, 8)
        ellipsoid(x, y, z, 0.09f, 0.09f, 0.045f, 0x394752)
    }

    private fun bucketBack(x: Float, z: Float, accent: Int, racing: Boolean) {
        fun point(u: Float, v: Float, back: Boolean): FloatArray {
            val y = 0.66f + v * 0.89f
            val w = 0.27f + 0.065f * sin(v * PI.toFloat()) - 0.06f * v * v
            return floatArrayOf(
                x + u * w,
                y,
                z + 0.18f + v * 0.12f - 0.12f * u * u + if (back) 0.07f else 0f,
            )
        }
        fun normal(u: Float, back: Boolean): FloatArray {
            val n = floatArrayOf(-u * 0.8f, 0.135f, -1f)
            val len = sqrt(n.sumOf { (it * it).toDouble() }).toFloat()
            return FloatArray(3) { n[it] / len * if (back) -1f else 1f }
        }
        val rows = if (racing) 22 else 14
        val columns = if (racing) 24 else 16
        for (back in listOf(false, true)) for (j in 0 until rows) for (i in 0 until columns) {
            val u = -1f + 2f * i / columns
            val v = j.toFloat() / rows
            val uu = u + 2f / columns
            val vv = v + 1f / rows
            // Two real harness slots cut through the shell, rather than painted-on black
            // rectangles.
            if (v in 0.66f..0.80f && abs(u + 0.04f) in 0.23f..0.63f) continue
            smoothFace(
                point(u, v, back) + point(uu, v, back) + point(uu, vv, back) + point(u, vv, back),
                normal(u, back) + normal(uu, back) + normal(uu, back) + normal(u, back),
                if (back) 0x51565B else 0x343A40,
                if (racing) 32 else 15,
            )
        }
        for (side in listOf(-1f, 1f)) for (j in 0 until rows) {
            val a = point(side, j.toFloat() / rows, false)
            val b = point(side, (j + 1).toFloat() / rows, false)
            tube(a, b, 0.016f, accent, 8)
        }
        if (!racing)
            for (side in listOf(-1, 1)) {
                tube(
                    floatArrayOf(x + side * 0.22f, 1.18f, z + 0.34f),
                    floatArrayOf(x + side * 0.05f, 1.01f, z + 0.32f),
                    0.012f,
                    accent,
                    8,
                )
                tube(
                    floatArrayOf(x + side * 0.05f, 0.94f, z + 0.29f),
                    floatArrayOf(x + side * 0.22f, 0.80f, z + 0.28f),
                    0.012f,
                    accent,
                    8,
                )
            }
    }

    private fun wideMonitor(x: Float, y: Float, z: Float, w: Float, h: Float, tile: Int) {
        for (i in 0 until 32) {
            val a = -w / 2 + i * w / 32
            val b = -w / 2 + (i + 1) * w / 32
            fun depth(xx: Float) = z + xx * xx * 0.10f
            val nx = -(a + b) * 0.10f
            val len = sqrt(nx * nx + 1)
            val begin = vertices.size
            face(
                floatArrayOf(
                    x + a,
                    y - h / 2,
                    depth(a),
                    x + b,
                    y - h / 2,
                    depth(b),
                    x + b,
                    y + h / 2,
                    depth(b),
                    x + a,
                    y + h / 2,
                    depth(a),
                ),
                floatArrayOf(nx / len, 0f, 1f / len),
                0xD3D9DB,
                tile,
                0.23f,
            )
            for (v in 0 until 6) {
                val corner = intArrayOf(0, 1, 2, 0, 2, 3)[v]
                val u = (i + if (corner == 1 || corner == 2) 1f else 0f) / 32
                vertices[begin + v * 12 + 9] = ((tile % 8) + (u * 0.98f + 0.01f)) / 8f
            }
            face(
                floatArrayOf(
                    x + b,
                    y - h / 2,
                    depth(b) - 0.045f,
                    x + a,
                    y - h / 2,
                    depth(a) - 0.045f,
                    x + a,
                    y + h / 2,
                    depth(a) - 0.045f,
                    x + b,
                    y + h / 2,
                    depth(b) - 0.045f,
                ),
                floatArrayOf(0f, 0f, -1f),
                0x1A2126,
            )
            box(
                x + (a + b) / 2,
                y + h / 2 + 0.007f,
                depth((a + b) / 2) - 0.016f,
                w / 32,
                0.016f,
                0.048f,
                0x11191E,
            )
            box(
                x + (a + b) / 2,
                y - h / 2 - 0.007f,
                depth((a + b) / 2) - 0.016f,
                w / 32,
                0.016f,
                0.048f,
                0x11191E,
            )
        }
    }

    private fun simulator(x: Float, z: Float, vip: Boolean = false) {
        if (vip) record("RacingVIP") else record("Racing")
        obstacles.add(TourObstacle(x, z, 1.7f, 2.8f))
        // Anodised aluminium extrusion cockpit: actual rails/grooves/fasteners, no chair casters.
        for (side in listOf(-1, 1)) {
            val xx = x + side * 0.48f
            roundedBox(xx, 0.12f, z, 0.10f, 0.11f, 2.65f, 0x464E52, 21, radius = 0.015f)
            for (groove in 0..2) box(
                xx + side * 0.055f,
                0.088f + groove * 0.028f,
                z,
                0.007f,
                0.008f,
                2.50f,
                0x151E23,
            )
            roundedBox(xx, 0.62f, z - 0.63f, 0.085f, 1.02f, 0.10f, 0x50585C, 21, radius = 0.01f)
            for (groove in 0..2) box(
                xx + side * 0.047f,
                0.62f,
                z - 0.664f + groove * 0.026f,
                0.006f,
                0.94f,
                0.009f,
                0x172027,
            )
            for (end in listOf(-1, 1)) {
                box(xx, 0.029f, z + end * 1.11f, 0.16f, 0.047f, 0.17f, 0x1B262C, 22)
                ellipsoid(
                    xx + side * 0.056f,
                    0.14f,
                    z + end * 1.04f,
                    0.013f,
                    0.035f,
                    0.035f,
                    0x9EA6AB,
                    21,
                )
            }
        }
        for (zz in listOf(z - 0.98f, z + 0.1f, z + 0.82f)) box(
            x,
            0.12f,
            zz,
            0.95f,
            0.10f,
            0.10f,
            0x40494E,
            21,
        )
        for (side in listOf(-1, 1)) {
            box(x + side * 0.22f, 0.28f, z + 0.46f, 0.05f, 0.055f, 0.86f, 0x7B858C, 21)
            box(x + side * 0.23f, 0.48f, z + 0.35f, 0.06f, 0.30f, 0.42f, 0x262F36)
        }
        roundedBox(x, 0.54f, z + 0.42f, 0.56f, 0.16f, 0.68f, 0x343A41, 15, radius = 0.13f)
        for (side in listOf(-1, 1)) ellipsoid(
            x + side * 0.27f,
            0.63f,
            z + 0.30f,
            0.11f,
            0.17f,
            0.60f,
            0x333A41,
            15,
        )
        bucketBack(x, z + 0.56f, if (vip) 0xB39768 else 0x6D8791, true)
        ellipsoid(x, 0.92f, z + 0.64f, 0.32f, 0.35f, 0.09f, 0x333B43, 15)
        box(x, 1.04f, z - 0.64f, 0.85f, 0.07f, 0.40f, 0x414B51, 21)
        roundedBox(x, 1.14f, z - 0.57f, 0.27f, 0.20f, 0.29f, 0x202A31, radius = 0.03f)
        wheel(x, 1.19f, z - 0.35f)
        for (side in listOf(-1, 1)) {
            box(x + side * 0.19f, 1.19f, z - 0.376f, 0.048f, 0.12f, 0.028f, 0x404F5A, 21)
            for (j in 0..2) ellipsoid(
                x + side * 0.10f,
                1.16f + j * 0.045f,
                z - 0.323f,
                0.020f,
                0.020f,
                0.010f,
                if (j == 0) 0xD6B044 else if (j == 1) 0x80B39C else 0xB65548,
            )
        }
        tube(
            floatArrayOf(x - 0.02f, 1.348f, z - 0.35f),
            floatArrayOf(x + 0.02f, 1.348f, z - 0.35f),
            0.014f,
            0xDBBF70,
            8,
        )
        // Angled pedal deck, three separate perforated pedals, and a gear lever on its own mount.
        face(
            floatArrayOf(
                x - 0.39f,
                0.19f,
                z - 0.62f,
                x + 0.39f,
                0.19f,
                z - 0.62f,
                x + 0.39f,
                0.32f,
                z - 1.12f,
                x - 0.39f,
                0.32f,
                z - 1.12f,
            ),
            floatArrayOf(0f, 0.96f, 0.25f),
            0x53616B,
            21,
        )
        for (i in -1..1) {
            val px = x + i * 0.23f
            box(px, 0.37f, z - 0.98f, 0.13f, 0.22f, 0.035f, 0xA3ABB0, 21)
            for (row in 0..3) for (col in 0..1) ellipsoid(
                px - 0.029f + col * 0.058f,
                0.30f + row * 0.042f,
                z - 0.957f,
                0.015f,
                0.015f,
                0.004f,
                0x28343E,
            )
            tube(
                floatArrayOf(px, 0.25f, z - 0.80f),
                floatArrayOf(px, 0.34f, z - 0.99f),
                0.022f,
                0x687780,
            )
        }
        box(x + 0.66f, 0.75f, z + 0.03f, 0.085f, 0.95f, 0.09f, 0x3E4A51, 21)
        box(x + 0.66f, 1.0f, z + 0.03f, 0.22f, 0.10f, 0.25f, 0x232E36)
        tube(
            floatArrayOf(x + 0.66f, 1.05f, z + 0.03f),
            floatArrayOf(x + 0.66f, 1.20f, z + 0.03f),
            0.017f,
            0xA3AFB5,
        )
        ellipsoid(x + 0.66f, 1.23f, z + 0.03f, 0.083f, 0.085f, 0.083f, 0x252F37, 15)
        for (side in listOf(-1, 1)) box(
            x + side * 0.73f,
            0.88f,
            z - 1.33f,
            0.065f,
            1.73f,
            0.085f,
            0x56616A,
            21,
        )
        box(x, 1.27f, z - 1.33f, 1.50f, 0.07f, 0.085f, 0x55616B, 21)
        wideMonitor(x, 1.55f, z - 1.28f, if (vip) 2.15f else 1.90f, 0.73f, 33)
        tube(
            floatArrayOf(x + 0.30f, 1.14f, z - 0.69f),
            floatArrayOf(x + 0.30f, 0.13f, z - 0.82f),
            0.008f,
            0x101B23,
            8,
        )
        box(x, 0.036f, z, 1.9f, 0.015f, 3.1f, 0x3A3F44, 23)
    }

    private fun arcade(x: Float, z: Float) {
        record("Arcade")
        box(x, 0.53f, z, 0.78f, 1.05f, 0.6f, 0x1B2230, solid = true)
        box(x, 1.43f, z - 0.09f, 0.82f, 0.8f, 0.44f, 0x263548)
        sign(x, 1.39f, z + 0.139f, 0.62f, 0.49f, 3)
        box(x, 1.91f, z, 0.84f, 0.18f, 0.57f, 0x42487B)
        sign(x, 1.92f, z + 0.301f, 0.72f, 0.13f, 18)
        box(x, 1.11f, z + 0.22f, 0.8f, 0.09f, 0.33f, 0x232D40)
        for (j in 0..3) ellipsoid(
            x - 0.08f + j * 0.09f,
            1.165f,
            z + 0.28f,
            0.047f,
            0.023f,
            0.047f,
            if (j % 2 == 0) 0xE47457 else 0x8BA8E1,
        )
        tube(
            floatArrayOf(x - 0.24f, 1.16f, z + 0.23f),
            floatArrayOf(x - 0.24f, 1.24f, z + 0.23f),
            0.015f,
            0x4A596D,
        )
        ellipsoid(x - 0.24f, 1.25f, z + 0.23f, 0.065f, 0.065f, 0.065f, 0xD85854)
    }

    fun kart(x: Float, z: Float, index: Int, parked: Boolean = true) {
        if (parked) record("GoKart")
        roundedBox(x, 0.23f, z, 1.05f, 0.15f, 1.65f, 0x272C33, radius = 0.13f, solid = parked)
        for (side in listOf(-1, 1)) {
            tube(
                floatArrayOf(x + side * 0.52f, 0.19f, z + 0.8f),
                floatArrayOf(x + side * 0.52f, 0.19f, z - 0.8f),
                0.035f,
                0x6A737A,
            )
            for (end in listOf(-1, 1)) {
                ellipsoid(
                    x + side * 0.64f,
                    0.25f,
                    z + end * 0.6f,
                    0.25f,
                    0.44f,
                    0.44f,
                    0x1B1E22,
                    22,
                )
                ellipsoid(
                    x + side * 0.772f,
                    0.25f,
                    z + end * 0.6f,
                    0.012f,
                    0.24f,
                    0.24f,
                    0x969EA2,
                    21,
                )
            }
        }
        val color = if (index % 2 == 0) 0xC54933 else 0x3E769B
        ellipsoid(x, 0.36f, z - 0.64f, 0.84f, 0.26f, 0.58f, color)
        ellipsoid(x - 0.52f, 0.34f, z - 0.03f, 0.26f, 0.26f, 0.75f, color)
        ellipsoid(x + 0.52f, 0.34f, z - 0.03f, 0.26f, 0.26f, 0.75f, color)
        box(x, 0.31f, z + 0.16f, 0.39f, 0.1f, 0.46f, 0x15191D, 22)
        ellipsoid(x, 0.56f, z + 0.42f, 0.42f, 0.48f, 0.13f, 0x15191D, 22)
        tube(floatArrayOf(x, 0.23f, z - 0.48f), floatArrayOf(x, 0.58f, z - 0.23f), 0.023f, 0x737B84)
        wheel(x, 0.63f, z - 0.21f)
        box(x + 0.31f, 0.43f, z + 0.62f, 0.29f, 0.24f, 0.34f, 0x92999B, 21)
        for (i in 0..4) box(
            x + 0.31f,
            0.53f + i * 0.017f,
            z + 0.62f,
            0.28f,
            0.007f,
            0.32f,
            0x515A62,
        )
        tube(
            floatArrayOf(x - 0.72f, 0.28f, z - 0.91f),
            floatArrayOf(x + 0.72f, 0.28f, z - 0.91f),
            0.06f,
            0x161C22,
        )
        sign(x, 0.35f, z - 0.941f, 0.22f, 0.18f, 24 + index)
        if (!parked) {
            roundedBox(x, 0.045f, z, 1.35f, 0.012f, 1.95f, 0x3F484B, radius = 0.23f)
            // Helmeted driver makes the steering/drift silhouette readable from the chase camera.
            ellipsoid(x, 0.80f, z + 0.14f, 0.34f, 0.54f, 0.24f, 0x26343F, 15)
            ellipsoid(x, 1.15f, z + 0.10f, 0.34f, 0.34f, 0.32f, 0xB6D875, 21)
            ellipsoid(x, 1.16f, z - 0.054f, 0.28f, 0.11f, 0.032f, 0x1F2D37, 21)
            for (side in listOf(-1, 1)) {
                tube(
                    floatArrayOf(x + side * 0.13f, 0.96f, z + 0.11f),
                    floatArrayOf(x + side * 0.23f, 0.80f, z - 0.03f),
                    0.058f,
                    0x34444D,
                )
                tube(
                    floatArrayOf(x + side * 0.23f, 0.80f, z - 0.03f),
                    floatArrayOf(x + side * 0.17f, 0.67f, z - 0.21f),
                    0.041f,
                    0x34444D,
                )
                ellipsoid(x + side * 0.17f, 0.66f, z - 0.22f, 0.085f, 0.08f, 0.11f, 0x161F26)
                tube(
                    floatArrayOf(x + side * 0.12f, 0.55f, z + 0.06f),
                    floatArrayOf(x + side * 0.18f, 0.29f, z - 0.48f),
                    0.062f,
                    0x283640,
                )
                ellipsoid(x + side * 0.18f, 0.24f, z - 0.52f, 0.14f, 0.11f, 0.27f, 0x161F26)
            }
        }
    }

    private fun cafe() {
        reception(7.3f, 8.3f, 4.8f)
        sign(7.3f, 2.95f, 8.3f, 3.5f, 0.53f, 8)
        box(6.1f, 1.45f, 8.2f, 0.75f, 0.61f, 0.49f, 0xABB3B4, 21)
        box(6.1f, 1.43f, 8.459f, 0.64f, 0.34f, 0.025f, 0x14202B)
        for (x in listOf(7.1f, 7.4f, 7.7f)) {
            tube(floatArrayOf(x, 1.17f, 8.43f), floatArrayOf(x, 1.35f, 8.43f), 0.055f, 0xD8D3C7, 16)
            ring(x, 1.36f, 8.43f, 0.056f, 0.045f, 0xD8D3C7)
        }
        for (x in listOf(4.5f, 8f)) {
            box(x, 0.77f, 13f, 1.3f, 0.06f, 0.8f, 0xA4937D, 2, solid = true)
            tube(floatArrayOf(x, 0.03f, 13f), floatArrayOf(x, 0.74f, 13f), 0.035f, 0x818B91)
            ring(x, 0.03f, 13f, 0.35f, 0f, 0x454D55)
            bench(x, 14.2f, 1.7f)
        }
    }

    private fun esportsEndPanel(x: Float, z: Float) {
        roundedBox(x, 0.42f, z, 0.13f, 0.80f, 0.95f, 0x1A232E, radius = 0.04f)
        for (side in listOf(-1, 1)) {
            val xx = x + side * 0.074f
            tube(
                floatArrayOf(xx, 0.18f, z - 0.32f),
                floatArrayOf(xx, 0.36f, z - 0.08f),
                0.023f,
                0x92E2F0,
                12,
            )
            tube(
                floatArrayOf(xx, 0.36f, z + 0.08f),
                floatArrayOf(xx, 0.18f, z + 0.32f),
                0.023f,
                0x92E2F0,
                12,
            )
            tube(
                floatArrayOf(xx, 0.65f, z - 0.32f),
                floatArrayOf(xx, 0.47f, z - 0.08f),
                0.023f,
                0x92E2F0,
                12,
            )
            tube(
                floatArrayOf(xx, 0.47f, z + 0.08f),
                floatArrayOf(xx, 0.65f, z + 0.32f),
                0.023f,
                0x92E2F0,
                12,
            )
        }
    }

    private fun privatePcRoom(room: PrivateRoomPlan.Room) {
        record("VIPRoom")
        val x = room.x
        val z = room.z
        box(x, 1.5f, z - 2.8f, 7.1f, 3f, 0.12f, 0x6A6E72, solid = true)
        for (side in listOf(-1, 1)) {
            box(x + side * 3.5f, 1.5f, z, 0.12f, 3f, 5.6f, 0x444C55, solid = true)
            box(x + side * 2.15f, 1.5f, z + 2.8f, 2.7f, 3f, 0.12f, 0x38444F, solid = true)
            box(x + side * 0.78f, 1.4f, z + 2.88f, 0.045f, 2.8f, 0.035f, 0xA8D7E3, glow = 0.65f)
        }
        box(x, 3.06f, z, 7f, 0.10f, 5.6f, 0x343D47)
        box(x, 2.91f, z, 4.8f, 0.025f, 1.5f, 0xE6E8E0, glow = 0.6f)
        sign(x, 2.68f, z + 2.9f, 1.45f, 0.30f, 5)
        // Open privacy door rests beside the frame rather than across the visitor's passage.
        roundedBox(
            x - 0.77f,
            1.35f,
            z + 2.12f,
            0.075f,
            2.6f,
            1.35f,
            0x425564,
            radius = 0.02f,
            solid = true,
        )
        for (i in 0 until room.capacity) {
            val px = x + if (i % 2 == 0) -1.8f else 1.8f
            val pz = z - 1.4f + (i / 2) * 2.2f
            deskBank(px, pz, 1)
            pc(px, pz, true)
        }
        if (room.capacity < 4) bench(x + 2.3f, z + 1.45f, 1.1f)
    }

    private fun gamingFloor() {
        shell(0x454A51)
        reception(-9.5f, 20.5f, 5f)
        lift()
        // Four orderly six-seat rows, two three-seat benches, clear central/cross aisles.
        for (z in listOf(15f, 10f, 5f, 0f)) for (center in listOf(-4f, 4f)) {
            deskBank(center, z, 3)
            for (seat in -1..1) pc(center + seat * 1.6f, z)
            for (side in listOf(-1, 1)) esportsEndPanel(center + side * 2.43f, z)
            pendant(center, z, 5.25f, 1.3f)
            // Bright recessed architectural panel like the reference club ceiling.
            box(center, 3.65f, z, 5.3f, 0.11f, 2.1f, 0x141D2B)
            face(
                floatArrayOf(
                    center - 2.5f,
                    3.58f,
                    z - 1f,
                    center + 2.5f,
                    3.58f,
                    z - 1f,
                    center + 2.5f,
                    3.58f,
                    z + 1f,
                    center - 2.5f,
                    3.58f,
                    z + 1f,
                ),
                floatArrayOf(0f, -1f, 0f),
                0xC5D9EC,
                34,
                0.5f,
            )
            for (side in listOf(-1, 1)) box(
                center + side * 2.60f,
                3.53f,
                z,
                0.03f,
                0.03f,
                2.1f,
                0xB7E5F0,
                glow = 0.8f,
            )
        }
        for (room in PrivateRoomPlan.rooms) privatePcRoom(room)
        for (x in listOf(-11f, 11f)) {
            bench(x, 16.5f, 2.8f)
            plant(x, 12.5f)
            for (z in listOf(4f, 11f, 18f)) {
                box(x, 1.72f, z, 0.1f, 3.1f, 0.24f, 0xCED1D0, solid = true)
                box(x, 1.73f, z + 0.14f, 0.04f, 2.8f, 0.018f, 0x72D9EE, glow = 0.7f)
            }
        }
        plant(-12.5f, 24f)
        plant(3f, 24f)
        washroom()
    }

    private fun billiardsFloor() {
        shell(0x30343A)
        for (z in listOf(4f, -3f)) for (x in listOf(-9f, -3f, 3f, 9f)) {
            pool(x, z, false)
            sign(x, 0.704f, z + 1.52f, 0.27f, 0.19f, 24 + (fixtures["Pool"]!! - 1))
        }
        for (x in listOf(-9f, -3f, 3f, 9f)) pool(x, -10f, true)
        lift()
        reception(-9f, 13.4f, 4.6f)
        sign(-9f, 2.7f, 13.39f, 3.3f, 0.51f, 8)
        for (x in listOf(-4f, 1f, 6f)) {
            bench(x, 16.3f, 3.2f)
            box(x, 0.42f, 14.9f, 1.35f, 0.075f, 0.65f, 0x8E7A64, 2, solid = true)
        }
        for (z in listOf(-13.5f, -6.5f, 0.5f, 7.5f)) {
            box(-6f, 1.92f, z, 0.36f, 3.84f, 0.36f, 0x242A31, solid = true)
            box(6f, 1.92f, z, 0.36f, 3.84f, 0.36f, 0x242A31, solid = true)
            sign(-6f, 2.19f, z + 0.19f, 0.3f, 0.67f, 3)
            sign(6f, 2.19f, z + 0.19f, 0.3f, 0.67f, 3)
        }
        for (z in listOf(-10f, -3f, 4f)) {
            box(-13.91f, 1.31f, z, 0.04f, 0.07f, 1.6f, 0x685541, 2)
            for (i in 0..5) tube(
                floatArrayOf(-13.84f, 0.35f, z - 0.6f + i * 0.23f),
                floatArrayOf(-13.84f, 1.82f, z - 0.6f + i * 0.23f),
                0.013f,
                0xC9AD7F,
                8,
            )
        }
        for (p in listOf(-13f to 16f, 5f to 12.8f, -13f to -16.5f)) plant(p.first, p.second)
        sign(0f, 2.85f, -17.87f, 4.5f, 0.58f, 19)
        washroom()
    }

    private fun loungeFloor() {
        shell(0x333844)
        for (z in listOf(10f, 4f, -2f, -8f)) console(-7.5f, z)
        for (x in listOf(4.4f, 8.4f)) for (z in listOf(-10f, -5f)) simulator(x, z)
        box(6.7f, 1.5f, -13.3f, 10f, 3f, 0.12f, 0x1E2A37, solid = true)
        sign(6.7f, 2.72f, -13.2f, 3.3f, 0.42f, 17)
        for (x in listOf(-10f, -7f, -4f, 4f, 7f, 10f)) arcade(x, 16f)
        floorPatch(6.4f, -7.3f, 8.7f, 15f, 0xBAAB97, 2)
        floorPatch(14.5f, 0f, 6.8f, 27f, 0xBAAB97, 2)
        for (z in listOf(9f, 3f, -3f, -9f)) {
            simulator(14.5f, z, true)
            box(17.3f, 1.5f, z, 0.12f, 3f, 5f, 0x535F69, solid = true)
            for (side in listOf(-1, 1)) {
                box(14.5f, 1.5f, z + side * 2.5f, 5.6f, 3f, 0.12f, 0x53616B, solid = true)
                box(11.7f, 1.5f, z + side * 1.64f, 0.12f, 3f, 1.72f, 0x394954, solid = true)
            }
            box(14.5f, 3.07f, z, 5.6f, 0.10f, 5f, 0x394A55)
            box(14.5f, 2.98f, z, 3.8f, 0.035f, 1f, 0xF0DEBB, glow = 0.55f)
        }
        washroom()
        cafe()
        lift()
        for (z in listOf(-7f, 4f, 12f)) pendant(0f, z, 2.3f, 1.5f)
        for (p in listOf(1.3f to 11f, 10.9f to -15.5f)) plant(p.first, p.second)
    }

    private fun outdoors() {
        // Continuous ground beyond the fence removes the old floating rooftop appearance.
        floor(500f, 500f, 0x8C9B76, 34)
        box(0f, -0.025f, 5f, 165f, 0.045f, 215f, 0x94A57A, 34)
        val points = KartTrack.points
        fun vertex(p: KartTrack.Point, y: Float) = floatArrayOf(p.x, y, p.z)
        for (i in points.indices) {
            val next = (i + 1) % points.size
            face(
                vertex(KartTrack.edge(i, KartTrack.halfRoad), 0.026f) +
                    vertex(KartTrack.edge(next, KartTrack.halfRoad), 0.026f) +
                    vertex(KartTrack.edge(next, -KartTrack.halfRoad), 0.026f) +
                    vertex(KartTrack.edge(i, -KartTrack.halfRoad), 0.026f),
                floatArrayOf(0f, 1f, 0f),
                0xB8BEC3,
                11,
            )
            for (side in listOf(-1, 1)) {
                val inner = side * KartTrack.halfRoad
                val outer = side * (KartTrack.halfRoad + 0.48f)
                face(
                    vertex(KartTrack.edge(i, inner), 0.035f) +
                        vertex(KartTrack.edge(next, inner), 0.035f) +
                        vertex(KartTrack.edge(next, outer), 0.035f) +
                        vertex(KartTrack.edge(i, outer), 0.035f),
                    floatArrayOf(0f, 1f, 0f),
                    if (i % 6 < 3) 0xC35B48 else 0xE5E2D7,
                )
                // Rubber barriers follow the same spline as the road and physics.
                val a = KartTrack.edge(i, side * (KartTrack.halfRoad + 0.9f))
                val b = KartTrack.edge(next, side * (KartTrack.halfRoad + 0.9f))
                if (!(side == -1 && a.z > 83f && abs(a.x) < 19f)) {
                    face(
                        vertex(a, 0.06f) + vertex(b, 0.06f) + vertex(b, 0.57f) + vertex(a, 0.57f),
                        floatArrayOf(side.toFloat(), 0f, 0f),
                        if (i % 12 < 6) 0x4F6269 else 0xCFD3CE,
                    )
                    face(
                        vertex(a, 0.57f) +
                            vertex(b, 0.57f) +
                            vertex(
                                KartTrack.edge(next, side * (KartTrack.halfRoad + 1.25f)),
                                0.57f,
                            ) +
                            vertex(KartTrack.edge(i, side * (KartTrack.halfRoad + 1.25f)), 0.57f),
                        floatArrayOf(0f, 1f, 0f),
                        0x2D3639,
                    )
                }
            }
        }
        // Checkered start line perpendicular to the first straight.
        for (i in 0..1) for (j in 0..11) box(
            i * 0.6f,
            0.052f,
            79.65f + j * 0.75f,
            0.6f,
            0.018f,
            0.75f,
            if ((i + j) % 2 == 0) 0xE7E5DC else 0x242B32,
        )
        for (gate in 1..8) {
            val p = KartTrack.gate(gate)
            val i = points.indexOf(p)
            val next = points[(i + 1) % points.size]
            val yaw = atan2(next.x - p.x, -(next.z - p.z))
            val begin = vertices.size
            for (side in listOf(-1, 1)) {
                box(side * 6.2f, 1.85f, 0f, 0.12f, 3.7f, 0.12f, 0x4C606A)
                box(side * 6.2f, 1.8f, 0.075f, 0.035f, 2.8f, 0.025f, 0x9ECDB5, glow = 0.5f)
                obstacles.add(
                    TourObstacle(
                        p.x + side * 6.2f * cos(yaw),
                        p.z + side * 6.2f * sin(yaw),
                        0.12f,
                        0.12f,
                    )
                )
            }
            box(0f, 3.75f, 0f, 12.5f, 0.13f, 0.18f, 0x4C606A)
            sign(0f, 3.54f, 0.10f, 1.25f, 0.43f, 23 + gate)
            transformY(begin, p.x, p.z, -yaw)
        }
        floorPatch(0f, 101f, 40f, 16f, 0xB8BEC3, 11)
        floorPatch(0f, 92f, 20f, 8f, 0xB8BEC3, 11)
        for (i in 0..5) {
            val x = -12.5f + i * 5f
            kart(x, 101f, i)
            for (side in listOf(-1, 1)) box(
                x + side * 1.5f,
                0.034f,
                101f,
                0.06f,
                0.013f,
                4.5f,
                0xE0DCBD,
            )
        }
        // Enterable washroom pavilion on the pedestrian side, clear of racing and chase cameras.
        floorPatch(-29.5f, 110f, 23f, 3.5f, 0xB8B4A9, 1)
        floorPatch(-38f, 108.7f, 8.4f, 4.6f, 0xB8B4A9, 1)
        washroom()
        box(0f, 3f, 113f, 8f, 0.25f, 0.6f, 0x303D42)
        for (x in listOf(-3.8f, 3.8f)) box(x, 1.5f, 113f, 0.16f, 3f, 0.16f, 0x526269)
        sign(0f, 2.65f, 112.65f, 6.5f, 0.5f, 10)
        // Walkway, grandstand, low boundary fences and landscaping anchor the circuit.
        for (row in 0..3) {
            box(38f, 0.3f + row * 0.43f, 97f + row * 1.4f, 21f, 0.25f, 1.1f, 0x829093)
            box(38f, 0.05f + row * 0.20f, 97f + row * 1.4f, 21f, 0.15f, 1.4f, 0x596968)
        }
        for (x in listOf(-80f, 80f)) {
            for (z in -105..110 step 7) {
                tube(
                    floatArrayOf(x, 0f, z.toFloat()),
                    floatArrayOf(x, 2.5f, z.toFloat()),
                    0.035f,
                    0x59695C,
                )
                tube(
                    floatArrayOf(x, 2.4f, z.toFloat()),
                    floatArrayOf(x, 2.4f, z + 7f),
                    0.022f,
                    0x72806C,
                )
                tube(
                    floatArrayOf(x, 1.1f, z.toFloat()),
                    floatArrayOf(x, 1.1f, z + 7f),
                    0.015f,
                    0x72806C,
                )
            }
        }
        for (x in listOf(-74f, 74f)) for (z in listOf(-94f, -40f, 20f, 77f, 109f)) {
            tube(floatArrayOf(x, 0f, z), floatArrayOf(x, 5.5f, z), 0.055f, 0x67767C)
            box(x, 5.5f, z, 0.9f, 0.10f, 0.6f, 0xDFDED0)
        }
        for (p in listOf(-75f to 96f, 68f to 103f, -74f to -100f, 73f to -95f)) {
            tube(
                floatArrayOf(p.first, 0f, p.second),
                floatArrayOf(p.first, 2.3f, p.second),
                0.15f,
                0x655547,
            )
            for (i in 0..6) {
                val angle = i * 2.4f
                val radius = if (i == 0) 0f else 1.25f
                ellipsoid(p.first + cos(angle) * radius, 3.2f + (i % 3) * 0.45f,
                    p.second + sin(angle) * radius, 2.7f, 2.7f, 2.7f,
                    if (i % 2 == 0) 0x647958 else 0x526C48, 34)
            }
        }
    }

    private fun floorPatch(x: Float, z: Float, w: Float, d: Float, color: Int, tile: Int) =
        box(x, 0.011f, z, w, 0.030f, d, color, tile)

    private fun transformY(begin: Int, x: Float, z: Float, radians: Float) {
        val c = cos(radians)
        val s = sin(radians)
        for (i in begin until vertices.size step 12) {
            val px = vertices.data[i]
            val pz = vertices.data[i + 2]
            val nx = vertices.data[i + 3]
            val nz = vertices.data[i + 5]
            vertices[i] = x + c * px + s * pz
            vertices[i + 2] = z - s * px + c * pz
            vertices[i + 3] = c * nx + s * nz
            vertices[i + 5] = -s * nx + c * nz
        }
    }

    init {
        if (buildScene)
            when (area) {
                TourArea.GAMING -> gamingFloor()
                TourArea.BILLIARDS -> billiardsFloor()
                TourArea.LOUNGE -> loungeFloor()
                TourArea.OUTDOOR -> outdoors()
            }
    }
}
