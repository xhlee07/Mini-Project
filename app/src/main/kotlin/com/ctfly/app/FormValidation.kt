package com.ctfly.app

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

fun bookingInputError(
    date: String,
    time: String,
    minutes: Int,
    guests: Int,
    capacity: Int,
    nowMillis: Long,
): String? {
    val now = Instant.ofEpochMilli(nowMillis).atZone(CafeZone)
    val day =
        runCatching { LocalDate.parse(date) }.getOrNull()
            ?: return "Reservation date is invalid. Choose a valid day, month and year."
    if (day < now.toLocalDate())
        return "Reservation date is in the past. Choose today or a future date."
    if (day > now.toLocalDate().plusDays(90))
        return "Reservation date must be within the next 90 days."
    val clock =
        runCatching { LocalTime.parse(time) }.getOrNull()
            ?: return "Start time is invalid. Choose an hour and minute."
    if (day.atTime(clock).atZone(CafeZone).toInstant().toEpochMilli() + 60000 <= nowMillis)
        return "Start time has already passed. Choose the current time or a later time."
    if (minutes < 30) return "Duration must be at least 30 minutes."
    if (guests !in 1..capacity) return "Players must be between 1 and $capacity for this space."
    return null
}

fun moneyInputSen(value: String): Int? =
    runCatching {
            if (!value.matches(Regex("[0-9]{1,7}(\\.[0-9]{1,2})?"))) return null
            value.toBigDecimal().multiply(100.toBigDecimal()).intValueExact()
        }
        .getOrNull()

fun validateFormFields(
    type: String,
    values: Map<String, String>,
    fields: List<FormField>,
): List<String> = buildList {
    for (f in fields) {
        val v = values[f.key].orEmpty().trim()
        if (v.isBlank()) {
            if (!f.optional) add("${f.label}: this field is required.")
            continue
        }
        if (f.kind == "number") {
            val n = v.toIntOrNull()
            if (n == null) add("${f.label}: enter a whole number.")
            else {
                val range =
                    when (f.key) {
                        "capacity" -> if (type == "save_event") 2..64 else 1..20
                        "per_user_limit" -> 1..100
                        "rate",
                        "points",
                        "quantity",
                        "usage_limit" -> 1..1000000
                        "percent" -> 1..100
                        "max_minutes" -> 0..180
                        else -> 0..10000000
                    }
                if (n !in range) add("${f.label}: use ${range.first}–${range.last}.")
            }
        }
        if (f.kind == "money" && moneyInputSen(v) == null)
            add("${f.label}: enter an amount in RM with at most two decimal places.")
        if (f.kind == "select" && f.options.none { it.first == v })
            add("${f.label}: choose an available option.")
        if (f.kind == "date" && runCatching { LocalDate.parse(v) }.isFailure)
            add("${f.label}: choose a valid date.")
        if (f.kind == "time" && runCatching { LocalTime.parse(v) }.isFailure)
            add("${f.label}: choose a valid hour and minute.")
        if (f.kind == "datetime" && runCatching { Instant.parse(v) }.isFailure)
            add("${f.label}: choose a valid date and time.")
        if (
            f.kind in listOf("text", "multiline") &&
                v.length >
                    (if (f.key.startsWith("prize_")) 800
                    else if (f.kind == "multiline") 2000 else 120)
        )
            add("${f.label}: this entry is too long.")
    }
    if (type in listOf("save_event", "schedule", "save_promotion")) {
        val start = runCatching { Instant.parse(values["starts_at"]) }.getOrNull()
        val end = runCatching { Instant.parse(values["ends_at"]) }.getOrNull()
        if (start != null && end != null && end <= start)
            add("End time must be later than start time.")
        if (type != "save_promotion" && start != null && start <= Instant.now())
            add("Start date / time must be in the future.")
        if (type == "save_promotion" && end != null && end <= Instant.now())
            add("Expiry date / time must be in the future.")
        val close = runCatching { Instant.parse(values["registration_closes_at"]) }.getOrNull()
        if (
            type == "save_event" &&
                close != null &&
                (close <= Instant.now() || (start != null && close > start))
        )
            add(
                "Registration closing date / time must be in the future and no later than the start."
            )
        if (
            type == "save_promotion" &&
                !values["code"].orEmpty().matches(Regex("[A-Za-z0-9-]{3,24}"))
        )
            add("Promo code: use 3–24 letters, numbers or hyphens.")
    }
}
