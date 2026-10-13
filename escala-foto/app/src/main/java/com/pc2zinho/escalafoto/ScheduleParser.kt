package com.pc2zinho.escalafoto

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime

data class OcrCell(val text: String, val box: Rect)

private val periodRegex = Regex("""(?i)PER[IÍ]ODO\s*:?\s*([0-3]?\d)[/.-]([01]?\d)[/.-](\d{2,4})\s*(?:A|À|ATE|ATÉ|-|–)\s*([0-3]?\d)[/.-]([01]?\d)[/.-](\d{2,4})""")
private val fullDateRegex = Regex("""\b([0-3]?\d)[/.-]([01]?\d)[/.-](\d{2,4})\b""")
private val timeRangeRegex = Regex("""(?i)\b([01]?\d|2[0-3])(?::([0-5]\d))?\s*(?:h)?\s*(?:-|–|—|AS|ÀS|A)\s*([01]?\d|2[0-3])(?::([0-5]\d))?\s*(?:h)?\b""")
private val offWordRegex = Regex("""(?i)\b(FOLGA|OFF|DESCANSO)\b""")

fun extractOcrCells(result: Text): List<OcrCell> =
    result.textBlocks.flatMap { block ->
        block.lines.flatMap { line ->
            line.elements.mapNotNull { element ->
                element.boundingBox?.let { OcrCell(element.text.trim(), it) }
            }
        }
    }.filter { it.text.isNotBlank() }


fun detectEmployeeCandidates(result: Text): List<String> {
    val cells = extractOcrCells(result)
    if (cells.isEmpty()) return emptyList()

    val shiftCells = cells.filter { timeRangeRegex.containsMatchIn(it.text) || offWordRegex.containsMatchIn(it.text) }
    if (shiftCells.isEmpty()) return emptyList()

    val minShiftX = shiftCells.minOf { it.box.centerX() }
    val topY = shiftCells.minOf { it.box.centerY() }
    val bottomY = shiftCells.maxOf { it.box.centerY() }
    val typicalHeight = shiftCells.map { it.box.height().coerceAtLeast(1) }.sorted().let { it[it.size / 2] }
    val rowTolerance = (typicalHeight * 1.2).toInt().coerceAtLeast(18)

    val left = cells.filter {
        it.box.centerX() < minShiftX &&
            it.box.centerY() in (topY - rowTolerance)..(bottomY + rowTolerance) &&
            it.text.any(Char::isLetter)
    }

    val rowCenters = shiftCells.map { it.box.centerY() }.sorted().fold(mutableListOf<Int>()) { acc, y ->
        if (acc.isEmpty() || kotlin.math.abs(acc.last() - y) > rowTolerance) acc.add(y)
        else acc[acc.lastIndex] = (acc.last() + y) / 2
        acc
    }

    val ignored = setOf(
        "FUNCIONARIO", "HOSPITAL", "VIDA MAIS", "LEGENDA", "PERIODO", "ESCALA DE TRABALHO",
        "UNIDADE", "ENFERMAGEM", "OBSERVACOES", "FOLGA", "TURNO", "RECURSOS HUMANOS",
        "INTERVALO", "SEG", "TER", "QUA", "QUI", "SEX", "SAB", "DOM"
    )

    return rowCenters.mapNotNull { center ->
        val parts = left.filter { kotlin.math.abs(it.box.centerY() - center) <= rowTolerance }
            .sortedBy { it.box.left }
            .map { it.text.trim() }
            .filter { it.any(Char::isLetter) }
        val candidate = parts.joinToString(" ").replace(Regex("""\s+"""), " ").trim()
        val normalized = normalize(candidate)
        candidate.takeIf {
            normalized.length in 3..50 &&
                normalized.count { ch -> ch == ' ' } <= 5 &&
                ignored.none { bad -> normalized.contains(bad) } &&
                normalized.any(Char::isLetter)
        }
    }.distinctBy { normalize(it) }.take(20)
}

fun parseScheduleOcr(result: Text, employeeName: String): List<Shift> {
    val cells = extractOcrCells(result)
    val fullText = result.text
    val period = detectPeriod(fullText)
    if (period != null && employeeName.isNotBlank()) {
        val geometric = parseGridByGeometry(cells, employeeName, period.first, period.second)
        if (geometric.isNotEmpty()) return geometric
    }
    return parseScheduleText(fullText, employeeName, period?.first?.year)
}

private fun parseGridByGeometry(
    cells: List<OcrCell>,
    employeeName: String,
    startDate: LocalDate,
    endDate: LocalDate
): List<Shift> {
    val normalizedName = normalize(employeeName)
    val nameCells = cells.filter {
        val n = normalize(it.text)
        n.contains(normalizedName) || normalizedName.contains(n)
    }
    if (nameCells.isEmpty()) return emptyList()

    val anchor = nameCells.maxByOrNull { normalize(it.text).length } ?: return emptyList()
    val rowHeight = anchor.box.height().coerceAtLeast(18)
    val rowCenter = anchor.box.centerY()
    val row = cells.filter {
        kotlin.math.abs(it.box.centerY() - rowCenter) <= rowHeight * 0.85 &&
            it.box.left > anchor.box.right - 4
    }.sortedBy { it.box.centerX() }

    if (row.isEmpty()) return emptyList()

    val tokens = mutableListOf<Pair<Int, ShiftToken>>()
    val joined = row.joinToString(" ") { it.text }
    timeRangeRegex.findAll(joined).forEach { m ->
        val matchText = m.value
        val matchingCell = row.firstOrNull { normalize(it.text).contains(normalize(matchText)) }
        val x = matchingCell?.box?.centerX() ?: Int.MAX_VALUE
        tokens += x to ShiftToken(
            start = parseTime(m.groupValues[1], m.groupValues[2]),
            end = parseTime(m.groupValues[3], m.groupValues[4]),
            off = false
        )
    }

    row.forEach { cell ->
        if (offWordRegex.containsMatchIn(cell.text)) {
            tokens += cell.box.centerX() to ShiftToken(null, null, true)
        } else {
            val m = timeRangeRegex.find(cell.text)
            if (m != null) {
                tokens += cell.box.centerX() to ShiftToken(
                    parseTime(m.groupValues[1], m.groupValues[2]),
                    parseTime(m.groupValues[3], m.groupValues[4]),
                    false
                )
            }
        }
    }

    val ordered = tokens.distinctBy { it.first to it.second }.sortedBy { it.first }.map { it.second }
    val dates = generateSequence(startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(endDate) }.toList()

    if (ordered.size < 2 || dates.isEmpty()) return emptyList()
    return dates.zip(ordered).map { (date, token) ->
        Shift(date = date, start = token.start, end = token.end, dayOff = token.off)
    }
}

private data class ShiftToken(val start: LocalTime?, val end: LocalTime?, val off: Boolean)

fun parseScheduleText(text: String, employeeName: String = "", preferredYear: Int? = null): List<Shift> {
    val period = detectPeriod(text)
    val year = period?.first?.year ?: preferredYear ?: LocalDate.now().year
    val lines = text.lines().map { it.replace(Regex("""\s+"""), " ").trim() }.filter { it.isNotBlank() }

    if (period != null && employeeName.isNotBlank()) {
        val employeeIndex = lines.indexOfFirst { normalize(it).contains(normalize(employeeName)) }
        if (employeeIndex >= 0) {
            val combined = buildString {
                append(lines[employeeIndex])
                for (i in 1..2) if (employeeIndex + i < lines.size) append(" ").append(lines[employeeIndex + i])
            }
            val tokens = extractTokens(combined)
            val dates = generateSequence(period.first) { it.plusDays(1) }.takeWhile { !it.isAfter(period.second) }.toList()
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
        val range = timeRangeRegex.find(line)
        when {
            range != null -> direct += Shift(date = date, start = parseTime(range.groupValues[1], range.groupValues[2]), end = parseTime(range.groupValues[3], range.groupValues[4]))
            offWordRegex.containsMatchIn(line) -> direct += Shift(date = date, dayOff = true)
        }
    }
    if (direct.isNotEmpty()) return direct.distinctBy { it.date to it.start }.sortedBy { it.date }

    // Never invent a date from an isolated time such as 19-07.
    // Without a detected period/full date, return nothing and require review.
    return emptyList()
}

private fun extractTokens(text: String): List<ShiftToken> {
    data class Positioned(val pos: Int, val token: ShiftToken)
    val found = mutableListOf<Positioned>()
    timeRangeRegex.findAll(text).forEach { m ->
        found += Positioned(m.range.first, ShiftToken(
            parseTime(m.groupValues[1], m.groupValues[2]),
            parseTime(m.groupValues[3], m.groupValues[4]),
            false
        ))
    }
    offWordRegex.findAll(text).forEach { m -> found += Positioned(m.range.first, ShiftToken(null, null, true)) }
    return found.sortedBy { it.pos }.map { it.token }
}

private fun detectPeriod(text: String): Pair<LocalDate, LocalDate>? {
    val match = periodRegex.find(text) ?: return null
    val start = makeDate(match.groupValues[1], match.groupValues[2], match.groupValues[3]) ?: return null
    val end = makeDate(match.groupValues[4], match.groupValues[5], match.groupValues[6]) ?: return null
    return if (!end.isBefore(start) && end.toEpochDay() - start.toEpochDay() <= 62) start to end else null
}

private fun fullDate(match: MatchResult): LocalDate? =
    makeDate(match.groupValues[1], match.groupValues[2], match.groupValues[3])

private fun makeDate(day: String, month: String, year: String): LocalDate? {
    val y = year.toIntOrNull()?.let { if (year.length == 2) 2000 + it else it } ?: return null
    return runCatching { LocalDate.of(y, month.toInt(), day.toInt()) }.getOrNull()
}

private fun parseTime(hour: String, minute: String): LocalTime =
    LocalTime.of(hour.toInt(), minute.toIntOrNull() ?: 0)

private fun normalize(value: String): String =
    Normalizer.normalize(value.uppercase(), Normalizer.Form.NFD)
        .replace(Regex("""\p{Mn}+"""), "")
        .replace(Regex("""[^A-Z0-9 ]"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
