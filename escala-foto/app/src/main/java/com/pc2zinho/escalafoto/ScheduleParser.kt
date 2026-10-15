package com.pc2zinho.escalafoto

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

data class OcrCell(val text: String, val box: Rect)
data class EmployeeRowCandidate(val label: String, val centerY: Int)

private data class ShiftToken(val start: LocalTime?, val end: LocalTime?, val off: Boolean)
private data class DateColumn(val date: LocalDate, val x: Int)

private val periodRegex = Regex("""(?i)PER[IÍ]ODO\s*:?[\s\S]{0,20}?([0-3]?\d)[/.-]([01]?\d)[/.-](\d{2,4})\s*(?:A|À|ATE|ATÉ|-|–|—)\s*([0-3]?\d)[/.-]([01]?\d)[/.-](\d{2,4})""")
private val fullDateRegex = Regex("""\b([0-3]?\d)[/.-]([01]?\d)[/.-](\d{2,4})\b""")
private val shortDateRegex = Regex("""\b([0-3]?\d)[/.-]([01]?\d)\b""")
private val timeRangeRegex = Regex("""(?i)\b([01]?\d|2[0-3])(?::([0-5]\d))?\s*(?:h)?\s*(?:-|–|—|AS|ÀS|A)\s*([01]?\d|2[0-3])(?::([0-5]\d))?\s*(?:h)?\b""")
private val clockRegex = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""")
private val offWordRegex = Regex("""(?i)\b(FOLGA|OFF|DESCANSO|DESC)\b""")
private val offCodes = setOf("F", "FO", "FOL", "FOLGA", "OFF", "DESC", "DESCANSO")

fun extractOcrCells(result: Text): List<OcrCell> =
    result.textBlocks.flatMap { block ->
        block.lines.flatMap { line ->
            line.elements.mapNotNull { element ->
                element.boundingBox?.let { OcrCell(element.text.trim(), it) }
            }
        }
    }.filter { it.text.isNotBlank() }

fun detectEmployeeRows(result: Text): List<EmployeeRowCandidate> {
    val cells = extractOcrCells(result)
    if (cells.isEmpty()) return emptyList()

    val scheduleLike = cells.filter { looksScheduleLike(it.text) }
    if (scheduleLike.isEmpty()) return emptyList()

    val typicalHeight = scheduleLike.map { it.box.height().coerceAtLeast(1) }
        .sorted().let { it[it.size / 2] }
    val tolerance = (typicalHeight * 1.15).toInt().coerceAtLeast(18)

    val centers = scheduleLike.map { it.box.centerY() }.sorted()
        .fold(mutableListOf<Int>()) { acc, y ->
            if (acc.isEmpty() || abs(acc.last() - y) > tolerance) acc.add(y)
            else acc[acc.lastIndex] = (acc.last() + y) / 2
            acc
        }

    val ignored = setOf(
        "FUNCIONARIO", "FUNCIONARIOS", "HOSPITAL", "VIDA MAIS", "LEGENDA", "PERIODO",
        "ESCALA DE TRABALHO", "UNIDADE", "ENFERMAGEM", "OBSERVACOES", "FOLGA", "TURNO",
        "RECURSOS HUMANOS", "INTERVALO", "SEG", "TER", "QUA", "QUI", "SEX", "SAB", "DOM"
    )

    return centers.mapNotNull { center ->
        val sameRow = cells.filter { abs(it.box.centerY() - center) <= tolerance }
        val scheduleX = sameRow.filter { looksScheduleLike(it.text) }.minOfOrNull { it.box.centerX() }
            ?: return@mapNotNull null

        val label = sameRow
            .filter { it.box.centerX() < scheduleX && it.text.any(Char::isLetter) }
            .sortedBy { it.box.left }
            .joinToString(" ") { it.text.trim() }
            .replace(Regex("""\s+"""), " ")
            .trim()

        val n = normalize(label)
        if (n.length in 2..50 && ignored.none { n.contains(it) }) {
            EmployeeRowCandidate(label, center)
        } else null
    }.distinctBy { normalize(it.label) }.take(24)
}

fun detectEmployeeCandidates(result: Text): List<String> =
    detectEmployeeRows(result).map { it.label }

fun parseScheduleOcrByRow(result: Text, rowCenterY: Int): List<Shift> {
    val cells = extractOcrCells(result)
    if (cells.isEmpty()) return emptyList()

    val period = detectPeriod(result.text)
    val row = cellsInRow(cells, rowCenterY)
    if (row.isEmpty()) return emptyList()

    // Preferred path: use real date columns in the header and intersect them with the chosen employee row.
    val headerColumns = detectDateColumns(cells, rowCenterY, period)
    if (headerColumns.size >= 3) {
        val parsed = parseUsingColumns(row, headerColumns)
        if (parsed.size >= 2) return parsed
    }

    // Fallback for tables whose header OCR lost the dates: rebuild an evenly spaced grid from the period.
    if (period != null) {
        val dates = datesBetween(period.first, period.second)
        if (dates.size >= 2) {
            val inferred = inferColumnsFromRow(row, dates)
            if (inferred.size >= 2) return inferred
        }
    }

    // Last safe fallback: if the OCR contains explicit dated entries on the selected row, use those.
    return parseDirectDatedRow(row, period?.first?.year)
}

fun parseScheduleOcr(result: Text, employeeName: String): List<Shift> {
    val cells = extractOcrCells(result)
    if (employeeName.isNotBlank()) {
        val anchor = findEmployeeAnchor(cells, employeeName)
        if (anchor != null) {
            val geometric = parseScheduleOcrByRow(result, anchor.box.centerY())
            if (geometric.isNotEmpty()) return geometric
        }
    }
    return parseScheduleText(result.text, employeeName, detectPeriod(result.text)?.first?.year)
}

private fun cellsInRow(cells: List<OcrCell>, rowCenterY: Int): List<OcrCell> {
    val nearby = cells.filter { abs(it.box.centerY() - rowCenterY) <= 60 }
    val typicalHeight = nearby.map { it.box.height().coerceAtLeast(1) }
        .sorted().let { if (it.isEmpty()) 18 else it[it.size / 2] }
    val tolerance = (typicalHeight * 1.35).toInt().coerceAtLeast(22)
    return cells.filter { abs(it.box.centerY() - rowCenterY) <= tolerance }
        .sortedBy { it.box.centerX() }
}

private fun detectDateColumns(
    cells: List<OcrCell>,
    rowCenterY: Int,
    period: Pair<LocalDate, LocalDate>?
): List<DateColumn> {
    val candidates = mutableListOf<DateColumn>()

    cells.asSequence()
        .filter { it.box.centerY() < rowCenterY - 8 }
        .forEach { cell ->
            parseDateFromCell(cell.text, period)?.let { date ->
                if (period == null || (!date.isBefore(period.first) && !date.isAfter(period.second))) {
                    candidates += DateColumn(date, cell.box.centerX())
                }
            }
        }

    val unique = candidates
        .sortedBy { it.x }
        .distinctBy { it.date }

    if (unique.size >= 3) return unique

    // Some rosters put only the day number (1..31) in the header.
    if (period != null) {
        val dates = datesBetween(period.first, period.second)
        val expectedDays = dates.map { it.dayOfMonth }.toSet()
        val numberCells = cells.asSequence()
            .filter { it.box.centerY() < rowCenterY - 8 }
            .mapNotNull { cell ->
                val value = cell.text.trim().toIntOrNull()
                if (value != null && value in expectedDays) Triple(cell, value, cell.box.centerY()) else null
            }.toList()

        if (numberCells.isNotEmpty()) {
            val bands = numberCells.groupBy { it.third / 24 }
            val best = bands.values.maxByOrNull { band ->
                val distinct = band.map { it.second }.distinct().size
                -abs(distinct - dates.size)
            }.orEmpty()

            val byDay = dates.associateBy { it.dayOfMonth }
            val dayColumns = best.mapNotNull { (cell, day, _) ->
                byDay[day]?.let { DateColumn(it, cell.box.centerX()) }
            }.sortedBy { it.x }.distinctBy { it.date }

            if (dayColumns.size >= 3) return dayColumns
        }
    }

    return unique
}

private fun parseUsingColumns(row: List<OcrCell>, columns: List<DateColumn>): List<Shift> {
    val sorted = columns.sortedBy { it.x }
    val result = mutableListOf<Shift>()

    sorted.forEachIndexed { index, column ->
        val left = if (index == 0) Int.MIN_VALUE else (sorted[index - 1].x + column.x) / 2
        val right = if (index == sorted.lastIndex) Int.MAX_VALUE else (column.x + sorted[index + 1].x) / 2

        val bucket = row.filter { it.box.centerX() in (left + 1) until right }
            .sortedBy { it.box.left }

        val token = parseShiftToken(bucket.joinToString(" ") { it.text })
        if (token != null) {
            result += Shift(date = column.date, start = token.start, end = token.end, dayOff = token.off)
        }
    }

    return result.distinctBy { it.date }.sortedBy { it.date }
}

private fun inferColumnsFromRow(row: List<OcrCell>, dates: List<LocalDate>): List<Shift> {
    val scheduleCells = row.filter { looksScheduleLike(it.text) }
    if (scheduleCells.size < 2) return emptyList()

    val minX = scheduleCells.minOf { it.box.centerX() }
    val maxX = scheduleCells.maxOf { it.box.centerX() }
    if (maxX <= minX) return emptyList()

    val centers = if (dates.size == 1) {
        listOf(minX)
    } else {
        val step = (maxX - minX).toDouble() / (dates.size - 1)
        dates.indices.map { i -> (minX + step * i).toInt() }
    }

    val columns = dates.indices.map { DateColumn(dates[it], centers[it]) }
    return parseUsingColumns(row, columns)
}

private fun parseDirectDatedRow(row: List<OcrCell>, preferredYear: Int?): List<Shift> {
    val joined = row.joinToString(" ") { it.text }
    val year = preferredYear ?: LocalDate.now().year
    val found = mutableListOf<Shift>()

    fullDateRegex.findAll(joined).forEach { match ->
        val date = fullDate(match) ?: return@forEach
        val after = joined.substring(match.range.last + 1).take(24)
        parseShiftToken(after)?.let { token ->
            found += Shift(date = date, start = token.start, end = token.end, dayOff = token.off)
        }
    }

    if (found.isEmpty()) {
        shortDateRegex.findAll(joined).forEach { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@forEach
            val month = match.groupValues[2].toIntOrNull() ?: return@forEach
            val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return@forEach
            val after = joined.substring(match.range.last + 1).take(24)
            parseShiftToken(after)?.let { token ->
                found += Shift(date = date, start = token.start, end = token.end, dayOff = token.off)
            }
        }
    }

    return found.distinctBy { it.date }.sortedBy { it.date }
}

private fun parseShiftToken(raw: String): ShiftToken? {
    val text = raw.trim().replace('–', '-').replace('—', '-')
    if (text.isBlank()) return null

    val normalized = normalize(text)
    if (offWordRegex.containsMatchIn(text) || normalized in offCodes) {
        return ShiftToken(null, null, true)
    }

    timeRangeRegex.find(text)?.let { m ->
        return ShiftToken(
            parseTime(m.groupValues[1], m.groupValues[2]),
            parseTime(m.groupValues[3], m.groupValues[4]),
            false
        )
    }

    val clocks = clockRegex.findAll(text).toList()
    if (clocks.size >= 2) {
        return ShiftToken(
            parseTime(clocks[0].groupValues[1], clocks[0].groupValues[2]),
            parseTime(clocks[1].groupValues[1], clocks[1].groupValues[2]),
            false
        )
    }

    // Handles OCR splits such as "07 - 19", "07 19" or "7h 19h".
    val numbers = Regex("""\b([0-2]?\d)\b""").findAll(text)
        .mapNotNull { it.groupValues[1].toIntOrNull() }
        .filter { it in 0..23 }
        .toList()

    if (numbers.size == 2) {
        return ShiftToken(LocalTime.of(numbers[0], 0), LocalTime.of(numbers[1], 0), false)
    }

    return null
}

private fun looksScheduleLike(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return false
    val normalized = normalize(trimmed)
    if (offWordRegex.containsMatchIn(trimmed) || normalized in offCodes) return true
    if (timeRangeRegex.containsMatchIn(trimmed) || clockRegex.containsMatchIn(trimmed)) return true
    if (trimmed.length <= 12 && trimmed.any(Char::isDigit)) return true
    return normalized in setOf("D", "N", "SD", "SN", "M", "T")
}

private fun findEmployeeAnchor(cells: List<OcrCell>, employeeName: String): OcrCell? {
    val target = normalize(employeeName)
    if (target.isBlank()) return null
    return cells.filter {
        val value = normalize(it.text)
        value.isNotBlank() && (value.contains(target) || target.contains(value))
    }.maxByOrNull { normalize(it.text).length }
}

fun parseScheduleText(text: String, employeeName: String = "", preferredYear: Int? = null): List<Shift> {
    val period = detectPeriod(text)
    val lines = text.lines()
        .map { it.replace(Regex("""\s+"""), " ").trim() }
        .filter { it.isNotBlank() }

    if (period != null && employeeName.isNotBlank()) {
        val employeeIndex = lines.indexOfFirst { normalize(it).contains(normalize(employeeName)) }
        if (employeeIndex >= 0) {
            val combined = buildString {
                append(lines[employeeIndex])
                for (i in 1..2) {
                    if (employeeIndex + i < lines.size) append(" ").append(lines[employeeIndex + i])
                }
            }
            val tokens = extractTokens(combined)
            val dates = datesBetween(period.first, period.second)
            if (tokens.size >= 2) {
                return dates.zip(tokens).map { (date, token) ->
                    Shift(date = date, start = token.start, end = token.end, dayOff = token.off)
                }
            }
        }
    }

    val direct = mutableListOf<Shift>()
    lines.forEach { line ->
        val dateMatch = fullDateRegex.find(line) ?: return@forEach
        val date = fullDate(dateMatch) ?: return@forEach
        val token = parseShiftToken(line.substring(dateMatch.range.last + 1))
        if (token != null) direct += Shift(date = date, start = token.start, end = token.end, dayOff = token.off)
    }
    if (direct.isNotEmpty()) return direct.distinctBy { it.date }.sortedBy { it.date }

    return emptyList()
}

private fun extractTokens(text: String): List<ShiftToken> {
    data class Positioned(val pos: Int, val token: ShiftToken)
    val found = mutableListOf<Positioned>()

    timeRangeRegex.findAll(text).forEach { m ->
        found += Positioned(
            m.range.first,
            ShiftToken(
                parseTime(m.groupValues[1], m.groupValues[2]),
                parseTime(m.groupValues[3], m.groupValues[4]),
                false
            )
        )
    }

    Regex("""(?i)\b(FOLGA|OFF|DESCANSO|DESC|FO|F)\b""").findAll(text).forEach { m ->
        found += Positioned(m.range.first, ShiftToken(null, null, true))
    }

    return found.sortedBy { it.pos }.map { it.token }
}

private fun detectPeriod(text: String): Pair<LocalDate, LocalDate>? {
    periodRegex.find(text)?.let { match ->
        val start = makeDate(match.groupValues[1], match.groupValues[2], match.groupValues[3])
        val end = makeDate(match.groupValues[4], match.groupValues[5], match.groupValues[6])
        if (start != null && end != null && !end.isBefore(start) && end.toEpochDay() - start.toEpochDay() <= 62) {
            return start to end
        }
    }

    // If OCR missed the word "PERÍODO", infer the visible range from complete dates.
    val dates = fullDateRegex.findAll(text).mapNotNull { fullDate(it) }.distinct().sorted().toList()
    if (dates.size >= 2) {
        val start = dates.first()
        val end = dates.last()
        if (!end.isBefore(start) && end.toEpochDay() - start.toEpochDay() <= 62) return start to end
    }

    return null
}

private fun parseDateFromCell(text: String, period: Pair<LocalDate, LocalDate>?): LocalDate? {
    fullDateRegex.find(text)?.let { return fullDate(it) }

    val short = shortDateRegex.find(text) ?: return null
    val day = short.groupValues[1].toIntOrNull() ?: return null
    val month = short.groupValues[2].toIntOrNull() ?: return null

    val years = if (period != null) {
        listOf(period.first.year - 1, period.first.year, period.second.year, period.second.year + 1).distinct()
    } else {
        listOf(LocalDate.now().year)
    }

    return years.mapNotNull { y -> runCatching { LocalDate.of(y, month, day) }.getOrNull() }
        .minByOrNull { date ->
            if (period == null) 0L
            else when {
                date.isBefore(period.first) -> period.first.toEpochDay() - date.toEpochDay()
                date.isAfter(period.second) -> date.toEpochDay() - period.second.toEpochDay()
                else -> 0L
            }
        }
}

private fun fullDate(match: MatchResult): LocalDate? =
    makeDate(match.groupValues[1], match.groupValues[2], match.groupValues[3])

private fun makeDate(day: String, month: String, year: String): LocalDate? {
    val y = year.toIntOrNull()?.let { if (year.length == 2) 2000 + it else it } ?: return null
    return runCatching { LocalDate.of(y, month.toInt(), day.toInt()) }.getOrNull()
}

private fun datesBetween(start: LocalDate, end: LocalDate): List<LocalDate> =
    generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()

private fun parseTime(hour: String, minute: String): LocalTime =
    LocalTime.of(hour.toInt(), minute.toIntOrNull() ?: 0)

private fun normalize(value: String): String =
    Normalizer.normalize(value.uppercase(), Normalizer.Form.NFD)
        .replace(Regex("""\p{Mn}+"""), "")
        .replace(Regex("""[^A-Z0-9 ]"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
