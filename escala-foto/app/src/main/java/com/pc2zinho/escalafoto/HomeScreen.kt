package com.pc2zinho.escalafoto

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun HomeScreen(
    padding: PaddingValues,
    shifts: List<Shift>,
    onImportClick: () -> Unit
) {
    val today = LocalDate.now()
    val month = YearMonth.now()
    val monthShifts = shifts.filter { YearMonth.from(it.date) == month && !it.dayOff }
    val hours = monthShifts.sumOf { it.durationHours() }
    val nextShift = shifts
        .filter { !it.dayOff && !it.date.isBefore(today) }
        .minByOrNull { it.date }
    val nextOff = shifts
        .filter { it.dayOff && !it.date.isBefore(today) }
        .minByOrNull { it.date }
    val formatter = DateTimeFormatter.ofPattern("EEE, dd 'de' MMM", Locale("pt", "BR"))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = "Escala Foto",
            fontSize = 29.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Sua escala pronta sem digitar tudo.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp
        )

        Spacer(Modifier.height(20.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14231F)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = "PRÓXIMO PLANTÃO",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                if (nextShift == null) {
                    Text("Nenhum plantão salvo ainda", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Importe um print ou foto da sua escala para começar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        nextShift.date.format(formatter).replaceFirstChar { it.uppercase() },
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatShiftTime(nextShift),
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onImportClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color(0xFF052117)
            )
        ) {
            Icon(Icons.Filled.AddAPhoto, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("IMPORTAR MINHA ESCALA", fontWeight = FontWeight.Black)
        }

        Spacer(Modifier.height(22.dp))
        Text("Resumo do mês", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MiniStat(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Schedule,
                title = "Horas",
                value = String.format(Locale.US, "%.0fh", hours)
            )
            MiniStat(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.TrendingUp,
                title = "Plantões",
                value = monthShifts.size.toString()
            )
        }

        Spacer(Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121B20))
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Bedtime,
                    contentDescription = null,
                    tint = Color(0xFFFFC857)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Próxima folga", fontWeight = FontWeight.Bold)
                    Text(
                        text = nextOff?.date?.format(DateTimeFormatter.ofPattern("dd/MM")) ?: "Ainda não identificada",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "Dica: quando a empresa mandar a escala no WhatsApp, salve o print e toque em “Importar minha escala”.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun MiniStat(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121B20))
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(value, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

fun formatShiftTime(shift: Shift): String {
    if (shift.dayOff) return "Folga"
    val start = shift.start?.toString()?.take(5) ?: "--:--"
    val end = shift.end?.toString()?.take(5) ?: "--:--"
    return start + " – " + end
}
