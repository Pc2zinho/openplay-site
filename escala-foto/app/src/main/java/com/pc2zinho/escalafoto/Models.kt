package com.pc2zinho.escalafoto

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class Shift(
    val id: Long = System.nanoTime(),
    val date: LocalDate,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val dayOff: Boolean = false
)

fun Shift.durationHours(): Double {
    if (dayOff || start == null || end == null) return 0.0
    val startDateTime = LocalDateTime.of(date, start)
    var endDateTime = LocalDateTime.of(date, end)
    if (!endDateTime.isAfter(startDateTime)) endDateTime = endDateTime.plusDays(1)
    return Duration.between(startDateTime, endDateTime).toMinutes() / 60.0
}
