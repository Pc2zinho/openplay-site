package com.pc2zinho.escalafoto

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.time.LocalTime

@Composable
fun ImportScreen(
    padding: PaddingValues,
    onSave: (List<Shift>) -> Unit
) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var employeeName by remember { mutableStateOf("") }
    var rawText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Selecione uma foto ou print da escala.") }
    var isReading by remember { mutableStateOf(false) }
    var parsed by remember { mutableStateOf<List<Shift>>(emptyList()) }
    var editingIndex by remember { mutableIntStateOf(-1) }

    fun processText(text: String) {
        rawText = text
        parsed = parseScheduleText(text, employeeName)
        status = if (parsed.isEmpty()) {
            "O texto foi lido, mas a escala não ficou clara. Confira o nome e tente outra imagem."
        } else {
            parsed.size.toString() + " itens identificados. Confira antes de salvar."
        }
    }

    fun readImage(uri: Uri) {
        selectedUri = uri
        isReading = true
        status = "Lendo a imagem no aparelho…"
        val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrElse {
            isReading = false
            status = "Não consegui abrir essa imagem."
            return
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                isReading = false
                processText(result.text)
                recognizer.close()
            }
            .addOnFailureListener {
                isReading = false
                status = "Não consegui reconhecer o texto. Tente um print mais nítido."
                recognizer.close()
            }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) readImage(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text("Importar escala", fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(
            "Foto ou print → leitura automática → conferência → calendário.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))

        OutlinedTextField(
            value = employeeName,
            onValueChange = { employeeName = it },
            label = { Text("Seu nome na escala (opcional)") },
            supportingText = { Text("Ajuda quando a imagem tem vários funcionários.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { picker.launch("image/*") },
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color(0xFF052117)
            )
        ) {
            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
            Spacer(Modifier.width(9.dp))
            Text("ESCOLHER FOTO OU PRINT", fontWeight = FontWeight.Black)
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                val example = "12/10 13/10 14/10 15/10\nJoão 07-19 Folga 07-19 Folga"
                processText(example)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Testar reconhecimento com exemplo")
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121B20))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isReading) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(24.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.width(12.dp))
                } else {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (parsed.isNotEmpty()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(status, fontSize = 14.sp)
            }
        }

        if (parsed.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text("Confira o que foi identificado", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))

            parsed.forEachIndexed { index, shift ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14231F))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                shift.date.toString(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                if (shift.dayOff) "Folga" else formatShiftTime(shift),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { editingIndex = index }) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("Editar")
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { onSave(parsed) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("SALVAR " + parsed.size + " ITENS NO CALENDÁRIO", fontWeight = FontWeight.Black)
            }
        }

        if (rawText.isNotBlank()) {
            Spacer(Modifier.height(22.dp))
            Text("Texto reconhecido", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F171B))
            ) {
                Text(
                    rawText,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        if (selectedUri != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                "A leitura é feita no próprio aparelho. A imagem não precisa ser enviada para um servidor.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }

    if (editingIndex >= 0 && editingIndex < parsed.size) {
        val current = parsed[editingIndex]
        ShiftEditDialog(
            shift = current,
            onDismiss = { editingIndex = -1 },
            onConfirm = { updated ->
                parsed = parsed.toMutableList().also { it[editingIndex] = updated }
                editingIndex = -1
            }
        )
    }
}

@Composable
private fun ShiftEditDialog(
    shift: Shift,
    onDismiss: () -> Unit,
    onConfirm: (Shift) -> Unit
) {
    var start by remember(shift) { mutableStateOf(shift.start?.toString()?.take(5).orEmpty()) }
    var end by remember(shift) { mutableStateOf(shift.end?.toString()?.take(5).orEmpty()) }
    var dayOff by remember(shift) { mutableStateOf(shift.dayOff) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Corrigir " + shift.date.toString()) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Folga", modifier = Modifier.weight(1f))
                    Switch(checked = dayOff, onCheckedChange = { dayOff = it })
                }
                if (!dayOff) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it },
                        label = { Text("Entrada (HH:mm)") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it },
                        label = { Text("Saída (HH:mm)") },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val startTime = runCatching { LocalTime.parse(normalizeTime(start)) }.getOrNull()
                val endTime = runCatching { LocalTime.parse(normalizeTime(end)) }.getOrNull()
                if (dayOff || (startTime != null && endTime != null)) {
                    onConfirm(
                        shift.copy(
                            start = if (dayOff) null else startTime,
                            end = if (dayOff) null else endTime,
                            dayOff = dayOff
                        )
                    )
                }
            }) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

private fun normalizeTime(value: String): String {
    val trimmed = value.trim().replace("h", ":", ignoreCase = true)
    if (trimmed.matches(Regex("""\d{1,2}:\d{2}"""))) {
        val parts = trimmed.split(":")
        return parts[0].padStart(2, '0') + ":" + parts[1]
    }
    if (trimmed.matches(Regex("""\d{1,2}"""))) return trimmed.padStart(2, '0') + ":00"
    return trimmed
}
