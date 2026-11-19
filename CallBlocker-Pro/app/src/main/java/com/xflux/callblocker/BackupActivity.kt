package com.xflux.callblocker

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val BackupNavy = Color(0xFF061D24)
private val BackupPanel = Color(0xFF0D3038)
private val BackupMint = Color(0xFF22D3B0)
private val BackupMuted = Color(0xFFB8CBCD)

class BackupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = BackupMint, background = BackupNavy, surface = BackupPanel)) {
                val scope = rememberCoroutineScope()
                var status by remember { mutableStateOf("Faça uma cópia antes de trocar de aparelho ou atualizar o aplicativo.") }
                var working by remember { mutableStateOf(false) }
                var pendingRestore by remember { mutableStateOf<Uri?>(null) }

                val createBackup = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    if (uri != null) scope.launch {
                        working = true
                        status = "Criando backup..."
                        try {
                            val output = contentResolver.openOutputStream(uri)
                                ?: error("Não foi possível criar o arquivo.")
                            val count = output.use { BackupManager.export(this@BackupActivity, it) }
                            status = "Backup concluído. $count registros do histórico incluídos."
                        } catch (error: Exception) {
                            status = "Falha ao criar backup: ${error.localizedMessage ?: "erro desconhecido"}"
                        } finally { working = false }
                    }
                }
                val chooseBackup = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri -> pendingRestore = uri }

                Column(
                    Modifier.fillMaxSize().background(BackupNavy).padding(20.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { finish() }) { Icon(Icons.Default.ArrowBack, "Voltar", tint = Color.White) }
                        Text("Backup e restauração", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    }
                    Spacer(Modifier.height(20.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = BackupPanel), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Seus dados protegidos", color = BackupMint, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "O arquivo inclui números permitidos, números bloqueados, estado da proteção, configurações e histórico de chamadas.",
                                color = Color.White
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "A restauração mescla os dados e evita repetir registros iguais. Ela não apaga as informações que já estão no aparelho.",
                                color = BackupMuted
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { createBackup.launch("CallBlockerPro-backup.json") },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Criar backup")
                    }
                    OutlinedButton(
                        onClick = { chooseBackup.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text("Restaurar backup")
                    }
                    Spacer(Modifier.height(16.dp))
                    if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(status, color = BackupMuted, modifier = Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Importante: guarde o arquivo em local seguro. O backup local não é sincronização automática na nuvem.",
                        color = BackupMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                pendingRestore?.let { uri ->
                    AlertDialog(
                        onDismissRequest = { pendingRestore = null },
                        title = { Text("Restaurar este backup?") },
                        text = { Text("Os dados do arquivo serão mesclados com os dados atuais. Registros duplicados serão ignorados.") },
                        confirmButton = {
                            TextButton(onClick = {
                                pendingRestore = null
                                scope.launch {
                                    working = true
                                    status = "Validando e restaurando backup..."
                                    try {
                                        val input = contentResolver.openInputStream(uri)
                                            ?: error("Não foi possível abrir o arquivo.")
                                        val inserted = input.use { BackupManager.restore(this@BackupActivity, it) }
                                        status = "Restauração concluída. $inserted novos registros de histórico importados."
                                    } catch (error: Exception) {
                                        status = "Backup não restaurado: ${error.localizedMessage ?: "arquivo inválido"}"
                                    } finally { working = false }
                                }
                            }) { Text("Restaurar") }
                        },
                        dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancelar") } }
                    )
                }
            }
        }
    }
}
