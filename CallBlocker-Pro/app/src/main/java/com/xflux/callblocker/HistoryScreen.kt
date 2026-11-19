package com.xflux.callblocker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HistoryMint = Color(0xFF22D3B0)
private val HistoryMuted = Color(0xFFB8CBCD)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    dao: BlockedDao,
    onChanged: () -> Unit,
    onAllowNumber: (String) -> Unit,
    onBlockNumber: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var calls by remember { mutableStateOf<List<BlockedCall>>(emptyList()) }
    var selected by remember { mutableStateOf<BlockedCall?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(refresh) { calls = dao.getAll() }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("CHAMADAS BLOQUEADAS", color = HistoryMint, fontWeight = FontWeight.Bold)
                Text("${calls.size} registros", color = HistoryMuted, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { confirmClear = true }, enabled = calls.isNotEmpty()) {
                Icon(Icons.Default.Delete, null); Spacer(Modifier.width(4.dp)); Text("Limpar")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (calls.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhuma chamada bloqueada até agora.", color = HistoryMuted)
            }
        } else {
            val grouped = calls.groupBy { SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")).format(Date(it.timestamp)) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                grouped.forEach { (day, entries) ->
                    item(key = "day-$day") {
                        Text(day.replaceFirstChar { it.uppercase(Locale("pt", "BR")) }, color = HistoryMint,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    }
                    items(entries, key = { it.id }) { call ->
                        Row(Modifier.fillMaxWidth().clickable { selected = call }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Call, null, tint = HistoryMint)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(call.number, color = Color.White, fontWeight = FontWeight.Medium)
                                Text(SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(call.timestamp)),
                                    color = HistoryMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Bloqueada", color = Color(0xFFFF817D), style = MaterialTheme.typography.labelSmall)
                        }
                        HorizontalDivider(color = Color(0xFF0D3038))
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Apagar todo o histórico?") },
            text = { Text("Essa ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { dao.deleteAll(); refresh++; onChanged() }
                }) { Text("Apagar tudo", color = Color(0xFFFF817D)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } }
        )
    }

    selected?.let { call ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            val attempts = calls.count { it.number == call.number }
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
                Text("Detalhes da chamada", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Text(call.number, style = MaterialTheme.typography.headlineSmall, color = HistoryMint)
                Text(SimpleDateFormat("dd/MM/yyyy 'às' HH:mm:ss", Locale("pt", "BR")).format(Date(call.timestamp)), color = HistoryMuted)
                Spacer(Modifier.height(12.dp))
                Text("Motivo: ${call.reason}", color = Color.White)
                Text("Tentativas registradas: $attempts", color = HistoryMuted)
                Spacer(Modifier.height(20.dp))
                if (call.number != "Número oculto") {
                    Button(onClick = {
                        onAllowNumber(call.number)
                        scope.launch { dao.deleteById(call.id); refresh++; onChanged() }
                        selected = null
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Permitir este número")
                    }
                    OutlinedButton(onClick = {
                        onBlockNumber(call.number)
                        selected = null
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Block, null); Spacer(Modifier.width(8.dp)); Text("Bloquear sempre este número")
                    }
                }
                OutlinedButton(onClick = {
                    scope.launch { dao.deleteById(call.id); refresh++; onChanged() }
                    selected = null
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Apagar do histórico")
                }
            }
        }
    }
}