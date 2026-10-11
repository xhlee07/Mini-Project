package com.ctfly.app

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Offline build utility; never runs on a user's phone. Regenerate after editing TourWorld. */
object TourMeshBaker {
    @JvmStatic
    fun main(args: Array<String>) {
        for (area in TourArea.entries) {
            val world = TourWorld(area)
            val bytes =
                ByteBuffer.allocate(16 + world.obstacles.size * 16 + world.vertices.size * 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
            bytes
                .putInt(0x43544632)
                .putInt(2)
                .putInt(world.obstacles.size)
                .putInt(world.vertices.size)
            for (o in world.obstacles) bytes.putFloat(o.x).putFloat(o.z).putFloat(o.w).putFloat(o.d)
            bytes.asFloatBuffer().put(world.vertices.data, 0, world.vertices.size)
            val target = File(File(args.single()), "campus-${area.name.lowercase()}.bin")
            target.parentFile?.mkdirs()
            target.writeBytes(bytes.array())
            println(
                "${area.name}: ${world.vertices.size / 12} vertices, ${world.obstacles.size} obstacles, ${bytes.capacity()} bytes, ${world.fixtures}"
            )
        }
    }
}
