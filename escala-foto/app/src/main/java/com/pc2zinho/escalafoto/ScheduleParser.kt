package com.pc2zinho.escalafoto

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private val dateRegex = Regex("""\b([0-3]?\d)[/.\-]([01]?\d)(?:[/.\-](\d{2,4}))?\b""")
private val rangeRegex = Regex(
    """\b([01]?\d|2[0-3])(?::([0-5]\d))?\s*(?:h)?\s*(?:-|–|—|às|as|a)\s*([01]?\d|2[0-3])(?::([0-5]\d))?\s*(?:h)?\b""",
    RegexOption.IGNORE_CASE
)
private val offRegex = Regex("""\b(folga|off|descanso)\b""", RegexOption.IGNORE_CASE)

fun parseScheduleText(text: String, employeeName: String = ""): List<Shift> {
    val lines = text.lines()
        .map { it.replace("\t", " ").replace(Regex("""\s+"""), " ").trim() }
        .filter { it.isNotBlank() }

    val direct = mutableListOf<Shift>()

    lines.forEach { line ->
        val dates = dateRegex.findAll(line).toList()
        if (dates.size != 1) return@forEach
        val date = dateFrom(dates.first()) ?: return@forEach
        val range = rangeRegex.find(line)
        when {
            range != null -> direct += Shift(date = date, start = timeFrom(range, 1, 2), end = timeFrom(range, 3, 4))
            offRegex.containsMatchIn(line) -> direct += Shift(date = date, dayOff = true)
        }
    }

    if (direct.isNotEmpty()) return direct.distinctSchedule().sortedBy { it.date }

    val headerIndex = lines.indexOfFirst { dateRegex.findAll(it).count() >= 2 }
    if (headerIndex >= 0) {
        val headerDates = dateRegex.findAll(lines[headerIndex]).mapNotNull(::dateFrom).toList()
        val row = when {
            employeeName.isNotBlank() -> lines.drop(headerIndex + 1)
                .firstOrNull { it.contains(employeeName, ignoreCase = true) }
            else -> lines.drop(headerIndex + 1).firstOrNull {
                rangeRegex.containsMatchIn(it) || offRegex.containsMatchIn(it)
            }
        }

        if (row != null && headerDates.isNotEmpty()) {
            data class Token(val index: Int, val start: LocalTime?, val end: LocalTime?, val off: Boolean)
            val tokens = mutableListOf<Token>()

            rangeRegex.findAll(row).forEach {
                tokens += Token(it.range.first, timeFrom(it, 1, 2), timeFrom(it, 3, 4), false)
            }
            offRegex.findAll(row).forEach {
                tokens += Token(it.range.first, null, null, true)
            }

            val ordered = tokens.sortedBy { it.index }
            val tableResult = headerDates.zip(ordered).map { (date, token) ->
                Shift(date = date, start = token.start, end = token.end, dayOff = token.off)
            }
            if (tableResult.isNotEmpty()) return tableResult.distinctSchedule().sortedBy { it.date }
        }
    }

    val allDates = dateRegex.findAll(text).mapNotNull(::dateFrom).toList()
    val allRanges = rangeRegex.findAll(text).toList()
    if (allDates.size == allRanges.size && allDates.isNotEmpty()) {
        return allDates.zip(allRanges).map { (date, range) ->
            Shift(date = date, start = timeFrom(range, 1, 2), end = timeFrom(range, 3, 4))
        }.distinctSchedule().sortedBy { it.date }
    }

    return emptyList()
}

private fun dateFrom(match: MatchResult): LocalDate? {
    val day = match.groupValues[1].toIntOrNull() ?: return null
    val month = match.groupValues[2].toIntOrNull() ?: return null
    val rawYear = match.groupValues.getOrNull(3).orEmpty()
    val now = LocalDate.now()
    var year = when {
        rawYear.isBlank() -> now.year
        rawYear.length == 2 -> 2000 + (rawYear.toIntOrNull() ?: return null)
        else -> rawYear.toIntOrNull() ?: return null
    }
    var date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
    if (rawYear.isBlank() && ChronoUnit.DAYS.between(date, now) > 180) {
        year += 1
        date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: date
    }
    return date
}

private fun timeFrom(match: MatchResult, hourIndex: Int, minuteIndex: Int): LocalTime {
    val hour = match.groupValues[hourIndex].toInt()
    val minute = match.groupValues[minuteIndex].toIntOrNull() ?: 0
    return LocalTime.of(hour, minute)
}

private fun List<Shift>.distinctSchedule(): List<Shift> =
    distinctBy { listOf(it.date, it.start, it.end, it.dayOff) }
