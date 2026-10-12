package com.pc2zinho.escalafoto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun EarningsScreen(
    padding: PaddingValues,
    shifts: List<Shift>
) {
    var hourlyRateText by remember { mutableStateOf("15,00") }
    val hourlyRate = hourlyRateText.replace(",", ".").toDoubleOrNull() ?: 0.0
    val month = YearMonth.now()
    val monthShifts = shifts.filter { YearMonth.from(it.date) == month && !it.dayOff }
    val totalHours = monthShifts.sumOf { it.durationHours() }
    val estimate = totalHours * hourlyRate
    val monthName = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("pt", "BR")))
        .replaceFirstChar { it.uppercase() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text("Ganhos", fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(
            "Uma estimativa simples baseada nas horas da sua escala.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))

        OutlinedTextField(
            value = hourlyRateText,
            onValueChange = { hourlyRateText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
            label = { Text("Valor aproximado por hora (R$)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14231F))
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(monthName, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "R$ " + String.format(Locale("pt", "BR"), "%.2f", estimate),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Estimativa do mês",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EarningsStat(
                modifier = Modifier.weight(1f),
                title = "Horas",
                value = String.format(Locale.US, "%.1f", totalHours)
            )
            EarningsStat(
                modifier = Modifier.weight(1f),
                title = "Plantões",
                value = monthShifts.size.toString()
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "Esse valor é apenas uma estimativa configurável. Adicional noturno, horas extras, impostos e regras de convenção coletiva não são calculados automaticamente nesta primeira versão.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun EarningsStat(
    modifier: Modifier,
    title: String,
    value: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121B20))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(value, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
