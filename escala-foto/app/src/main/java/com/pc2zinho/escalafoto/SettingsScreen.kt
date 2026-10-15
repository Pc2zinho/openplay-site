package com.pc2zinho.escalafoto

import android.content.Intent
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    shifts: List<Shift>,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text("Perfil", fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(
            "Configurações e dados da sua escala.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14231F))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Privacidade por padrão", fontWeight = FontWeight.Bold)
                    Text(
                        "OCR local no aparelho nesta versão.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        OutlinedButton(
            onClick = {
                val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                val body = buildString {
                    append("Minha escala\n\n")
                    shifts.sortedBy { it.date }.forEach { shift ->
                        append(shift.date.format(formatter))
                        append(" — ")
                        append(if (shift.dayOff) "Folga" else formatShiftTime(shift))
                        append("\n")
                    }
                    append("\nGerado pelo Escala Foto")
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Minha escala")
                    putExtra(Intent.EXTRA_TEXT, body)
                }
                context.startActivity(Intent.createChooser(intent, "Compartilhar escala"))
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = shifts.isNotEmpty(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Compartilhar minha escala")
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = { confirmClear = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = shifts.isNotEmpty(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Apagar dados salvos")
        }

        Spacer(Modifier.height(22.dp))

        Text("Escala Foto • versão 0.1.0", fontWeight = FontWeight.Bold)
        Text(
            "Protótipo funcional: calendário, OCR offline, leitura inicial de escalas, correção manual, cálculo de horas e compartilhamento.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Apagar sua escala?") },
            text = { Text("Os plantões e folgas salvos neste aparelho serão removidos.") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    confirmClear = false
                }) {
                    Text("Apagar")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
