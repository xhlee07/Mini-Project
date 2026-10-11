package com.ctfly.app

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Prebuilt static geometry and collision volumes. No procedural modelling on the UI/GL thread. */
class TourMesh(context: Context, area: TourArea) {
    val obstacles: List<TourObstacle>
    val geometry: FloatBuffer
    val floatCount: Int

    init {
        context.assets.open(area.asset).use { stream ->
            val bytes = stream.readBytes()
            val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            check(data.int == 0x43544632 && data.int == 2) { "Unsupported tour mesh" }
            val count = data.int
            floatCount = data.int
            check(count in 1..1000 && floatCount in 36..24_000_000 && floatCount % 36 == 0)
            check(bytes.size == 16 + count * 16 + floatCount * 4)
            obstacles = List(count) { TourObstacle(data.float, data.float, data.float, data.float) }
            geometry =
                ByteBuffer.allocateDirect(floatCount * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
            geometry.put(data.asFloatBuffer())
            geometry.position(0)
        }
    }
}
