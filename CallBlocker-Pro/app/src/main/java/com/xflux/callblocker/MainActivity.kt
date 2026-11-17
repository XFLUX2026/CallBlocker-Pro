package com.xflux.callblocker

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Navy = Color(0xFF061D24)
private val Panel = Color(0xFF0D3038)
private val Mint = Color(0xFF22D3B0)
private val Lime = Color(0xFF9AF36A)
private val Muted = Color(0xFFB8CBCD)
private val Danger = Color(0xFFFF817D)

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
        var allowNumber by remember { mutableStateOf("") }
        var blockNumber by remember { mutableStateOf("") }
        var selectedTab by remember { mutableIntStateOf(0) }
        var refresh by remember { mutableIntStateOf(0) }
        var blockedCount by remember { mutableIntStateOf(0) }
        val scope = rememberCoroutineScope()
        val historyDao = remember { AppDb.get(this@MainActivity).blockedDao() }
        val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            active = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        }
        val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasContactsPermission = it
            refresh++
        }
        val allowedNumbers = remember(refresh) { AllowList.manual(this@MainActivity).toList().sorted() }
        val blockedNumbers = remember(refresh) { BlockList.all(this@MainActivity).toList().sorted() }

        LaunchedEffect(Unit) {
            active = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            hasContactsPermission = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            HistoryMigration.importLegacy(this@MainActivity, historyDao)
            blockedCount = historyDao.count()
        }

        MaterialTheme(colorScheme = darkColorScheme(primary = Mint, background = Navy, surface = Panel, onSurface = Color.White)) {
            Scaffold(containerColor = Navy, bottomBar = {
                NavigationBar(containerColor = Panel) {
                    NavigationBarItem(selectedTab == 0, { selectedTab = 0 }, { Icon(Icons.Default.Shield, null) }, label = { Text("Proteção") })
                    NavigationBarItem(selectedTab == 1, { selectedTab = 1 }, { Icon(Icons.Default.CheckCircle, null) }, label = { Text("Permitidos") })
                    NavigationBarItem(selectedTab == 2, { selectedTab = 2 }, { Icon(Icons.Default.Block, null) }, label = { Text("Bloqueados") })
                    NavigationBarItem(selectedTab == 3, { selectedTab = 3 }, { Icon(Icons.Default.History, null) }, label = { Text("Histórico") })
                }
            }) { padding ->
                Column(Modifier.fillMaxSize().background(Navy).padding(padding).padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text("CallBlocker Pro", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("Sua linha, suas regras.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    when (selectedTab) {
                        0 -> {
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    RadarShield(active = active, modifier = Modifier.size(176.dp))
                                    Text(if (active) "PROTEÇÃO ATIVADA" else "PROTEÇÃO DESATIVADA",
                                        color = if (active) Lime else Danger, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                    Text(
                                        if (active) "Seu aparelho está protegido contra chamadas indesejadas."
                                        else "Desativado: risco de receber ligações indesejadas.",
                                        color = if (active) Muted else Danger,
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick = {
                                        if (!active) roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                                    }, enabled = !active, modifier = Modifier.fillMaxWidth()) {
                                        Icon(if (active) Icons.Default.Shield else Icons.Default.PowerSettingsNew, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (active) "Proteção ligada" else "Ativar proteção")
                                    }
                                    Text("$blockedCount chamadas bloqueadas", color = Lime, modifier = Modifier.padding(top = 10.dp))
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("CENTRAL DE PROTEÇÃO", color = Mint, fontWeight = FontWeight.Bold)
                            InfoRow(Icons.Default.CheckCircle, "Permitidos", "Seus contatos e números liberados.", Lime)
                            InfoRow(Icons.Default.Block, "Bloqueio personalizado", "Números que você escolher são barrados primeiro.", Danger)
                            InfoRow(Icons.Default.Security, "Filtro automático", "Outros números não autorizados são rejeitados.", Mint)
                            Spacer(Modifier.weight(1f))
                            if (!hasContactsPermission) {
                                OutlinedButton(onClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Contacts, null); Spacer(Modifier.width(8.dp)); Text("Autorizar leitura dos contatos")
                                }
                            }
                            Text("O Android precisa manter o CallBlocker Pro como filtro de chamadas para a proteção funcionar.",
                                color = Muted, fontSize = 11.sp)
                        }
                        1 -> {
                            Text("NÚMEROS PERMITIDOS", color = Mint, fontWeight = FontWeight.Bold)
                            Text("Estes números podem ligar mesmo sem estar na sua agenda.", color = Muted, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            if (!hasContactsPermission) {
                                Button(onClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Autorizar acesso aos contatos")
                                }
                            } else Text("Contatos da agenda: liberados automaticamente", color = Lime, fontSize = 12.sp)
                            OutlinedTextField(value = allowNumber, onValueChange = { allowNumber = it },
                                label = { Text("Número com DDD") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                AllowList.add(this@MainActivity, allowNumber)
                                BlockList.remove(this@MainActivity, allowNumber)
                                allowNumber = ""; refresh++
                            }, enabled = allowNumber.any { it.isDigit() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Adicionar aos permitidos")
                            }
                            Spacer(Modifier.height(12.dp))
                            LazyColumn(Modifier.weight(1f)) {
                                items(allowedNumbers, key = { it }) { item ->
                                    NumberRow(item, Icons.Default.CheckCircle, Lime) {
                                        AllowList.remove(this@MainActivity, item); refresh++
                                    }
                                }
                            }
                        }
                        2 -> {
                            Text("BLOQUEIO PERSONALIZADO", color = Danger, fontWeight = FontWeight.Bold)
                            Text("Adicione qualquer número que você não deseja receber. Esta lista tem prioridade sobre os permitidos e contatos.",
                                color = Muted, fontSize = 12.sp)
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(value = blockNumber, onValueChange = { blockNumber = it },
                                label = { Text("Número que deseja bloquear") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                BlockList.add(this@MainActivity, blockNumber)
                                AllowList.remove(this@MainActivity, blockNumber)
                                blockNumber = ""; refresh++
                            }, enabled = blockNumber.any { it.isDigit() },
                                colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Navy),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Icon(Icons.Default.Block, null); Spacer(Modifier.width(8.dp)); Text("Bloquear este número")
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("${blockedNumbers.size} números na sua lista de bloqueio", color = Muted, fontSize = 12.sp)
                            LazyColumn(Modifier.weight(1f)) {
                                items(blockedNumbers, key = { it }) { item ->
                                    NumberRow(item, Icons.Default.Block, Danger) {
                                        BlockList.remove(this@MainActivity, item); refresh++
                                    }
                                }
                            }
                        }
                        else -> {
                            HistoryScreen(dao = historyDao, onChanged = {
                                refresh++
                                scope.launch { blockedCount = historyDao.count() }
                            }, onAllowNumber = {
                                AllowList.add(this@MainActivity, it)
                                BlockList.remove(this@MainActivity, it)
                                refresh++
                            }, onBlockNumber = {
                                BlockList.add(this@MainActivity, it)
                                AllowList.remove(this@MainActivity, it)
                                refresh++
                            })
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun NumberRow(number: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onDelete: () -> Unit) {
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint)
            Text(number, color = Color.White, modifier = Modifier.weight(1f).padding(start = 10.dp))
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, null, tint = Muted) }
        }
        HorizontalDivider(color = Panel)
    }

    @Composable
    private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, tint: Color) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RadarShield(active: Boolean, modifier: Modifier = Modifier) {
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (active) 360f else 0f,
        animationSpec = if (active) {
            infiniteRepeatable(
                animation = tween(2800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            tween(durationMillis = 0)
        },
        label = "radarRotation"
    )
    val radarColor = if (active) Lime else Danger
    val radarGlow = if (active) Mint else Danger
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * .43f
            drawCircle(radarColor.copy(alpha = if (active) .10f else .05f), radius = radius * 1.12f, center = center)
            drawCircle(radarColor.copy(alpha = .45f), radius = radius, center = center, style = Stroke(width = 1.5.dp.toPx()))
            drawCircle(radarColor.copy(alpha = .25f), radius = radius * .66f, center = center, style = Stroke(width = 1.dp.toPx()))
            drawCircle(radarColor.copy(alpha = .20f), radius = radius * .33f, center = center, style = Stroke(width = 1.dp.toPx()))
            drawLine(radarColor.copy(alpha = .25f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx())
            drawLine(radarColor.copy(alpha = .25f), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.dp.toPx())
            if (active) {
                rotate(rotation, pivot = center) {
                    drawArc(radarGlow.copy(alpha = .24f), -90f, 105f, true,
                        topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2))
                    drawLine(radarColor, center, Offset(center.x, center.y - radius), 2.dp.toPx())
                }
            } else {
                drawLine(radarColor.copy(alpha = .8f), Offset(center.x, center.y - radius * .72f),
                    Offset(center.x, center.y + radius * .72f), 2.dp.toPx())
            }
            if (active) {
                drawCircle(Lime, radius = 3.dp.toPx(), center = Offset(center.x + radius * .52f, center.y - radius * .34f))
                drawCircle(Mint, radius = 2.5.dp.toPx(), center = Offset(center.x - radius * .58f, center.y + radius * .18f))
            }
        }
        Box(
            Modifier.size(82.dp).background(
                if (active) Color(0xFF104B3B) else Color(0xFF4B2024),
                CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Shield,
                null,
                tint = radarColor,
                modifier = Modifier.size(58.dp)
            )
        }
    }
}
