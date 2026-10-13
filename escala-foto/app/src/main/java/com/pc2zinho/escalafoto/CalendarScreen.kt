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
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.provider.CalendarContract
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
                    val work = monthShifts.filter { !it.dayOff && it.start != null && it.end != null }
                    if (work.isNotEmpty()) {
                        val first = work.first()
                        val startMillis = first.date.atTime(first.start).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        var endDate = first.date
                        if (!first.end!!.isAfter(first.start)) endDate = endDate.plusDays(1)
                        val endMillis = endDate.atTime(first.end).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                            .putExtra(CalendarContract.Events.TITLE, "Plantão")
                            .putExtra(CalendarContract.Events.DESCRIPTION, "Escala importada pelo Escala Foto")
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A765))
            ) {
                Icon(Icons.Filled.EventAvailable, contentDescription = null)
                Text("  ADICIONAR PLANTÃO AO CALENDÁRIO DO CELULAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
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
