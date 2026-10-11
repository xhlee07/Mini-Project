package com.ctfly.app

/** The same room capacities/names as migration 005; prices are whole-room hourly sen. */
object PrivateRoomPlan {
    data class Room(
        val name: String,
        val capacity: Int,
        val rate: Int,
        val x: Float,
        val z: Float,
    ) {
        val doorPose
            get() = TourPose(x, z + 3.4f, 0f, -.08f, TourArea.GAMING)

        val insidePose
            get() = TourPose(x, z + 1.2f, 0f, -.22f, TourArea.GAMING)
    }

    val rooms =
        listOf(1, 2, 4).flatMapIndexed { row, capacity ->
            val label =
                when (capacity) {
                    1 -> "Solo"
                    2 -> "Duo"
                    else -> "Squad"
                }
            val rate =
                when (capacity) {
                    1 -> 1500
                    2 -> 2800
                    else -> 5200
                }
            listOf(-9f, 0f, 9f).mapIndexed { index, x ->
                Room("VIP Room $label %02d".format(index + 1), capacity, rate, x, -5f - row * 7f)
            }
        }
}
