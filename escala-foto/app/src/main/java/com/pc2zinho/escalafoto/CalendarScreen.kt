package com.pc2zinho.escalafoto

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.time.ZoneId
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class CalendarCell(val date: LocalDate?)

@Composable
fun CalendarScreen(
    padding: PaddingValues,
    shifts: List<Shift>
) {
    val context = LocalContext.current
    var month by remember { mutableStateOf(YearMonth.now()) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var showReplaceDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    fun exportMonth() { exportMessage = exportWorkShiftsToDeviceCalendar(context, shifts.filter { YearMonth.from(it.date) == month }, replace = false) }
    fun replaceMonth() { exportMessage = exportWorkShiftsToDeviceCalendar(context, shifts.filter { YearMonth.from(it.date) == month }, replace = true) }
    fun removeMonth() { exportMessage = removeEscalaFotoEventsFromDeviceCalendar(context, month) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.READ_CALENDAR] == true && grants[Manifest.permission.WRITE_CALENDAR] == true) exportMonth()
        else exportMessage = "Permissão do calendário necessária para adicionar todos os plantões."
    }
    val today = LocalDate.now()
    val firstDay = month.atDay(1)
    val offset = firstDay.dayOfWeek.value - 1
    val cells = buildList {
        repeat(offset) { add(CalendarCell(null)) }
        for (day in 1..month.lengthOfMonth()) add(CalendarCell(month.atDay(day)))
    }
    val monthTitle = month.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale("pt", "BR")))
        .replaceFirstChar { it.uppercase() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Mês anterior")
            }
            Text(monthTitle, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Próximo mês")
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth()) {
            listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM").forEach {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = it,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            userScrollEnabled = true
        ) {
            items(cells) { cell ->
                val date = cell.date
                val shift = date?.let { d -> shifts.firstOrNull { it.date == d } }
                val isToday = date == today
                val background = when {
                    date == null -> Color.Transparent
                    shift?.dayOff == true -> Color(0xFF18CFE3)
                    shift != null -> Color(0xFF16A765)
                    else -> Color(0xFF111A1F)
                }
                val dayColor = when {
                    date == null -> Color.Transparent
                    shift?.dayOff == true -> Color(0xFF04171A)
                    shift != null -> Color.White
                    else -> Color(0xFFF3F7F5)
                }

                Card(
                    modifier = Modifier.aspectRatio(0.80f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = background)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = date?.dayOfMonth?.toString().orEmpty(),
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = dayColor
                        )
                        Spacer(Modifier.height(5.dp))
                        if (shift != null) {
                            Text(
                                text = if (shift.dayOff) "FOLGA" else shift.start?.toString()?.take(5).orEmpty(),
                                color = if (shift.dayOff) Color(0xFF04171A) else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                maxLines = 1
                            )
                            if (!shift.dayOff) {
                                Text(
                                    text = shift.end?.toString()?.take(5).orEmpty(),
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        val monthShifts = shifts.filter { YearMonth.from(it.date) == month }
        if (monthShifts.isNotEmpty()) {
            Button(
                onClick = {
                    val readOk = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                    val writeOk = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
                    if (readOk && writeOk) exportMonth()
                    else calendarPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A765))
            ) {
                Icon(Icons.Filled.EventAvailable, contentDescription = null)
                Text("  ADICIONAR TODOS AO CALENDÁRIO DO CELULAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showReplaceDialog = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Sync, contentDescription = null)
                    Text(" Substituir", fontSize = 10.sp)
                }
                OutlinedButton(onClick = { showRemoveDialog = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                    Text(" Remover", fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            exportMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
            Spacer(Modifier.height(10.dp))
            Text(
                text = monthShifts.count { !it.dayOff }.toString() + " plantões • " +
                    monthShifts.count { it.dayOff }.toString() + " folgas identificadas",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

    if (showReplaceDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceDialog = false },
            title = { Text("Substituir plantões?") },
            text = { Text("Os plantões deste mês criados pelo Escala Foto serão removidos do calendário do celular e a escala atual será adicionada novamente.") },
            confirmButton = { TextButton(onClick = { showReplaceDialog = false; replaceMonth() }) { Text("Substituir") } },
            dismissButton = { TextButton(onClick = { showReplaceDialog = false }) { Text("Cancelar") } }
        )
    }
    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remover do calendário?") },
            text = { Text("Isso remove somente os eventos deste mês criados pelo Escala Foto. Sua escala continuará salva dentro do aplicativo.") },
            confirmButton = { TextButton(onClick = { showRemoveDialog = false; removeMonth() }) { Text("Remover") } },
            dismissButton = { TextButton(onClick = { showRemoveDialog = false }) { Text("Cancelar") } }
        )
    }
}

private fun removeEscalaFotoEventsFromDeviceCalendar(context: android.content.Context, month: YearMonth): String {
    val resolver = context.contentResolver
    val zone = ZoneId.systemDefault()
    val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val selection = CalendarContract.Events.DTSTART + ">=? AND " + CalendarContract.Events.DTSTART + "<? AND " +
        CalendarContract.Events.DESCRIPTION + " LIKE ?"
    val removed = resolver.delete(
        CalendarContract.Events.CONTENT_URI,
        selection,
        arrayOf(start.toString(), end.toString(), "ESCALA_FOTO_EVENT%")
    )
    return if (removed > 0) removed.toString() + " plantões removidos do calendário do celular."
    else "Nenhum plantão criado pelo Escala Foto foi encontrado neste mês."
}


private fun exportWorkShiftsToDeviceCalendar(context: android.content.Context, shifts: List<Shift>, replace: Boolean): String {
    val work = shifts.filter { !it.dayOff && it.start != null && it.end != null }.sortedBy { it.date }
    if (work.isEmpty()) return "Nenhum plantão deste mês para exportar."
    val resolver = context.contentResolver
    if (replace && work.isNotEmpty()) removeEscalaFotoEventsFromDeviceCalendar(context, YearMonth.from(work.first().date))
    val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
    val selection = CalendarContract.Calendars.VISIBLE + "=1 AND " + CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + ">=?"
    val calendarId = resolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection,
        arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), CalendarContract.Calendars.IS_PRIMARY + " DESC"
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
        ?: return "Não encontrei um calendário gravável no aparelho."
    var added = 0
    var skipped = 0
    val zone = ZoneId.systemDefault()
    work.forEach { shift ->
        val start = shift.date.atTime(shift.start).atZone(zone).toInstant().toEpochMilli()
        var endDate = shift.date
        if (!shift.end!!.isAfter(shift.start)) endDate = endDate.plusDays(1)
        val end = endDate.atTime(shift.end).atZone(zone).toInstant().toEpochMilli()
        val duplicateSelection = CalendarContract.Events.CALENDAR_ID + "=? AND " + CalendarContract.Events.DTSTART + "=? AND " + CalendarContract.Events.TITLE + "=?"
        val duplicate = resolver.query(CalendarContract.Events.CONTENT_URI, arrayOf(CalendarContract.Events._ID),
            duplicateSelection, arrayOf(calendarId.toString(), start.toString(), "Plantão"), null
        )?.use { it.moveToFirst() } ?: false
        if (duplicate) skipped++ else {
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, "Plantão")
                put(CalendarContract.Events.DESCRIPTION, "ESCALA_FOTO_EVENT • Escala importada pelo Escala Foto")
                put(CalendarContract.Events.DTSTART, start)
                put(CalendarContract.Events.DTEND, end)
                put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            }
            if (resolver.insert(CalendarContract.Events.CONTENT_URI, values) != null) added++
        }
    }
    return when {
        added > 0 && skipped > 0 -> added.toString() + " plantões adicionados • " + skipped + " já estavam no calendário."
        added > 0 -> added.toString() + " plantões adicionados ao calendário do celular."
        skipped > 0 -> "Todos os plantões já estavam no calendário. Nenhum duplicado foi criado."
        else -> "Não foi possível adicionar os plantões."
    }
}
