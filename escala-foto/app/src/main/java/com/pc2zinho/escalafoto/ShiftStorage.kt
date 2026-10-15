package com.pc2zinho.escalafoto

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime

class ShiftStorage(context: Context) {
    private val prefs = context.getSharedPreferences("escala_foto_store", Context.MODE_PRIVATE)

    fun load(): List<Shift> {
        val raw = prefs.getString("shifts", "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val p = line.split("|")
            if (p.size < 4) return@mapNotNull null
            runCatching {
                Shift(
                    date = LocalDate.parse(p[0]),
                    start = p[1].takeIf { it.isNotBlank() }?.let(LocalTime::parse),
                    end = p[2].takeIf { it.isNotBlank() }?.let(LocalTime::parse),
                    dayOff = p[3].toBoolean()
                )
            }.getOrNull()
        }.sortedBy { it.date }.toList()
    }

    fun save(shifts: List<Shift>) {
        val raw = shifts
            .distinctBy { listOf(it.date, it.start, it.end, it.dayOff) }
            .sortedBy { it.date }
            .joinToString("\n") {
                "${it.date}|${it.start ?: ""}|${it.end ?: ""}|${it.dayOff}"
            }
        prefs.edit().putString("shifts", raw).apply()
    }

    fun clear() {
        prefs.edit().remove("shifts").apply()
    }
}
