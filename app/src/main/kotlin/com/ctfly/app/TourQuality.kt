package com.ctfly.app

import kotlin.math.*

/** Render pixels, independent of Compose's display density and saved camera state. */
enum class TourQuality(val label: String, private val edge: Int, private val pixels: Int) {
    ULTRA("Ultra", 4096, 6_000_000),
    BALANCED("Balanced", 1920, 2_000_000),
    SMOOTH("Smooth", 1280, 1_000_000);

    fun size(width: Int, height: Int): Pair<Int, Int> {
        val w = max(1, width)
        val h = max(1, height)
        val scale =
            min(1.0, min(edge.toDouble() / max(w, h), sqrt(pixels.toDouble() / (w.toDouble() * h))))
        return max(1, (w * scale).roundToInt()) to max(1, (h * scale).roundToInt())
    }

    companion object {
        fun parse(value: String) = entries.firstOrNull { it.name == value } ?: ULTRA
    }
}
