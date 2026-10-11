package com.ctfly.app

/** Stable physical locations used by tour navigation and booking filters. */
enum class TourArea(
    val label: String,
    val floor: String,
    val halfWidth: Float,
    val halfDepth: Float,
    val summary: String,
) {
    GAMING("Gaming", "L1", 14f, 34f, "24 arena PCs · 9 private rooms for 1–4 players"),
    BILLIARDS("Billiards", "L2", 14f, 26f, "8 pool tables · 4 snooker tables"),
    LOUNGE(
        "Social lounge",
        "G",
        18f,
        17f,
        "4 console suites · 4 standard & 4 VIP simulators · 6 arcade stations",
    ),
    OUTDOOR(
        "Kart circuit",
        "G",
        85f,
        115f,
        "Ground level · long circuit · playable kart time trial",
    );

    val asset
        get() = "tour/campus-${name.lowercase()}.bin"

    companion object {
        fun parse(value: String?) = entries.find { it.name == value } ?: GAMING
    }
}

object VenueDirectory {
    val kinds = listOf("PC", "VIPRoom", "Pool", "Snooker", "Console", "Racing", "Arcade", "GoKart")

    fun durations(kind: String) =
        when (kind) {
            "GoKart" -> listOf(30, 60)
            "Arcade" -> listOf(30, 60, 90, 120)
            else -> listOf(30, 60, 90, 120, 180)
        }

    fun area(kind: String) =
        when (kind) {
            "Pool",
            "Snooker" -> TourArea.BILLIARDS
            "Console",
            "Racing",
            "Arcade" -> TourArea.LOUNGE
            "GoKart" -> TourArea.OUTDOOR
            else -> TourArea.GAMING
        }

    fun label(kind: String) =
        when (kind) {
            "GoKart" -> "Go-kart"
            "Racing" -> "Racing simulator"
            "VIPRoom" -> "VIP room"
            else -> kind
        }
}
