package com.ctfly.app

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val CafeZone: ZoneId = ZoneId.of("Asia/Kuala_Lumpur")

fun money(sen: Int): String = String.format(Locale.US, "RM %,.2f", sen / 100.0)

fun adult(dob: String, today: LocalDate = LocalDate.now(CafeZone)): Boolean =
    runCatching {
            LocalDate.parse(dob).let {
                !it.isAfter(today.minusYears(18)) && !it.isBefore(today.minusYears(120))
            }
        }
        .getOrDefault(false)

fun tier(spent: Int): String =
    when {
        spent >= 100000 -> "Gold"
        spent >= 30000 -> "Silver"
        else -> "Bronze"
    }

fun dateLabel(iso: String): String =
    runCatching {
            ZonedDateTime.parse(iso)
                .withZoneSameInstant(CafeZone)
                .format(DateTimeFormatter.ofPattern("dd MMM · h:mm a", Locale.ENGLISH))
        }
        .getOrDefault(iso.take(16))

fun bookingPrice(rate: Int, minutes: Int): Int = (rate * minutes + 30) / 60
