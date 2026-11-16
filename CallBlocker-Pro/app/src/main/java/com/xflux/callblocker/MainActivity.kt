package com.xflux.callblocker

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Navy = Color(0xFF061D24)
private val Panel = Color(0xFF0D3038)
private val Mint = Color(0xFF22D3B0)
private val Lime = Color(0xFF9AF36A)
private val Muted = Color(0xFFB8CBCD)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CallBlockerScreen() }
    }

    @Composable
    private fun CallBlockerScreen() {
        val roleManager = getSystemService(RoleManager::class.java)
        var active by remember { mutableStateOf(roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) }
        var hasContactsPermission by remember { mutableStateOf(checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) }
        var number by remember { mutableStateOf("") }
        var selectedTab by remember { mutableIntStateOf(0) }
        var refresh by remember { mutableIntStateOf(0) }
        var blockedCount by remember { mutableIntStateOf(0) }
        val screenScope = rememberCoroutineScope()
        val historyDao = remember { AppDb.get(this@MainActivity).blockedDao() }
        val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            active = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        }
        val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasContactsPermission = it
            refresh++
        }
        val manualNumbers = remember(refresh) { AllowList.manual(this@MainActivity).toList().sorted() }
        LaunchedEffect(Unit) {
            active = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            hasContactsPermission = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            HistoryMigration.importLegacy(this@MainActivity, historyDao)
            blockedCount = historyDao.count()
        }
        MaterialTheme(colorScheme = darkColorScheme(primary = Mint, background = Navy, surface = Panel)) {
            Scaffold(containerColor = Navy, bottomBar = {
                NavigationBar(containerColor = Panel) {
                    NavigationBarItem(selectedTab == 0, { selectedTab = 0 }, { Icon(Icons.Default.Shield, null) }, label = { Text("Proteção") })
                    NavigationBarItem(selectedTab == 1, { selectedTab = 1 }, { Icon(Icons.Default.Group, null) }, label = { Text("Permitidos") })
                    NavigationBarItem(selectedTab == 2, { selectedTab = 2 }, { Icon(Icons.Default.History, null) }, label = { Text("Histórico") })
                }
            }) { padding ->
                Column(Modifier.fillMaxSize().background(Navy).padding(padding).padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text("CallBlocker Pro", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("Sua linha, suas regras.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(18.dp))
                    if (selectedTab == 0) {
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(104.dp).background(Color(0xFF104B4B), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Shield, null, tint = Mint, modifier = Modifier.size(66.dp))
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(if (active) "PROTEÇÃO ATIVA" else "PROTEÇÃO DESATIVADA", color = if (active) Mint else Lime, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text("Somente números autorizados podem ligar", color = Muted, fontSize = 13.sp)
                                Spacer(Modifier.height(14.dp))
                                Button(onClick = {
                                    if (!active) roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                                }, enabled = !active, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (active) "Proteção ligada" else "Ativar proteção")
                                }
                                Text("$blockedCount chamadas bloqueadas", color = Lime, modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("COMO FUNCIONA", color = Mint, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        InfoRow(Icons.Default.CheckCircle, "Contatos e números autorizados", "Podem ligar normalmente.", Lime)
                        InfoRow(Icons.Default.Block, "Todos os outros números", "Rejeitados silenciosamente, inclusive ocultos.", Color(0xFFFF817D))
                        InfoRow(Icons.Default.Wifi, "Internet e WhatsApp", "Continuam funcionando normalmente.", Mint)
                        Spacer(Modifier.weight(1f))
                        if (!hasContactsPermission) {
                            OutlinedButton(onClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Contacts, null); Spacer(Modifier.width(8.dp)); Text("Autorizar leitura dos contatos")
                            }
                        }
                        Text("O Android precisa definir este app como filtro de chamadas. A proteção só funciona depois da ativação.", color = Muted, fontSize = 11.sp)
                    } else if (selectedTab == 1) {
                        Text("CONTATOS AUTORIZADOS", color = Mint, fontWeight = FontWeight.Bold)
                        Text("Contatos salvos na agenda são liberados automaticamente quando o acesso está autorizado.", color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        if (!hasContactsPermission) {
                            Button(onClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }, modifier = Modifier.fillMaxWidth()) { Text("Permitir acesso aos contatos") }
                        } else Text("Acesso à agenda autorizado", color = Lime, fontSize = 12.sp)
                        Spacer(Modifier.height(18.dp))
                        OutlinedTextField(value = number, onValueChange = { number = it }, label = { Text("Número com DDD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { AllowList.add(this@MainActivity, number); number = ""; refresh++ }, enabled = number.any { it.isDigit() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Adicionar número permitido")
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("NÚMEROS ADICIONADOS", color = Mint, fontWeight = FontWeight.Bold)
                        LazyColumn(Modifier.weight(1f)) {
                            items(manualNumbers, key = { it }) { item ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, null, tint = Mint)
                                    Text(item, color = Color.White, modifier = Modifier.weight(1f).padding(start = 10.dp))
                                    IconButton(onClick = { AllowList.remove(this@MainActivity, item); refresh++ }) { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF817D)) }
                                }
                                HorizontalDivider(color = Panel)
                            }
                        }
                    } else {
                        HistoryScreen(dao = historyDao, onChanged = {
                            refresh++
                            screenScope.launch { blockedCount = historyDao.count() }
                        }, onAllowNumber = { AllowList.add(this@MainActivity, it) })
                    }
                }
            }
        }
    }

    @Composable
    private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, tint: Color) {
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(25.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
        }
    }
}
