package com.shieldios.adblocker.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldios.adblocker.data.AdBlockState
import com.shieldios.adblocker.data.BlocklistManager
import com.shieldios.adblocker.data.DnsLogEntry
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ShieldAppRoot(onToggleVpn: () -> Unit) {
    val colors = LocalIosColors.current
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            IosBottomTabBar(
                selectedTab = selectedTab,
                onSelect = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (selectedTab) {
                0 -> ProtectionTab(onToggleVpn = onToggleVpn)
                1 -> ActivityTab()
                2 -> ListsTab()
                3 -> SettingsTab()
            }
        }
    }
}

@Composable
private fun IosBottomTabBar(selectedTab: Int, onSelect: (Int) -> Unit) {
    val colors = LocalIosColors.current
    val items = listOf(
        Triple("Protección", Icons.Rounded.Shield, 0),
        Triple("Actividad", Icons.Rounded.History, 1),
        Triple("Listas", Icons.Rounded.List, 2),
        Triple("Ajustes", Icons.Rounded.Settings, 3)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.tabBarBackground)
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = colors.separator)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            items.forEach { (label, icon, index) ->
                val isSelected = selectedTab == index
                val tint = if (isSelected) colors.blue else colors.labelSecondary
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(index) }
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = tint
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtectionTab(onToggleVpn: () -> Unit) {
    val colors = LocalIosColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isRunning by AdBlockState.isVpnRunning.collectAsState()
    val blockedCount by AdBlockState.blockedCount.collectAsState()
    val totalQueries by AdBlockState.totalQueries.collectAsState()
    val domainCount by AdBlockState.domainCount.collectAsState()
    val isUpdating by AdBlockState.isUpdatingLists.collectAsState()
    val lastUpdated by AdBlockState.lastUpdated.collectAsState()
    val recentLogs by AdBlockState.recentLogs.collectAsState()

    val numberFormat = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }
    val blockPercent = if (totalQueries > 0) ((blockedCount * 100.0) / totalQueries) else 0.0

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val ringColor by animateColorAsState(
        targetValue = if (isRunning) colors.green else colors.labelSecondary,
        label = "ringColor"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item {
            Text(
                text = "Protección",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = colors.labelPrimary,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )
        }

        // Botón principal circular estilo iOS
        item {
            IosGroupedCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isRunning) {
                            Box(
                                modifier = Modifier
                                    .size(144.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(colors.green.copy(alpha = 0.16f))
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(124.dp)
                                .shadow(12.dp, CircleShape)
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        if (isRunning) listOf(Color(0xFF34C759), Color(0xFF248A3D))
                                        else listOf(Color(0xFF8E8E93), Color(0xFF636366))
                                    )
                                )
                                .clickable { onToggleVpn() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PowerSettingsNew,
                                contentDescription = "Activar o desactivar protección",
                                tint = Color.White,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(ringColor.copy(alpha = 0.14f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isRunning) "Protección ACTIVA" else "Protección desactivada",
                            color = ringColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "VPN local DNS · Nada sale de tu teléfono",
                        fontSize = 13.sp,
                        color = colors.labelSecondary
                    )
                }
            }
        }

        // Tarjetas de estadísticas
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Bloqueados",
                    value = numberFormat.format(blockedCount),
                    accent = colors.red,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Consultas",
                    value = numberFormat.format(totalQueries),
                    accent = colors.blue,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Tasa de bloqueo",
                    value = String.format(Locale.getDefault(), "%.1f%%", blockPercent),
                    accent = colors.green,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Dominios",
                    value = numberFormat.format(domainCount),
                    accent = colors.orange,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Botón actualizar listas
        item {
            Spacer(modifier = Modifier.height(14.dp))
            IosGroupedCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isUpdating) {
                            scope.launch { BlocklistManager.updateLists(context) }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Actualizar listas de anuncios",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.blue
                        )
                        Text(
                            text = lastUpdated,
                            fontSize = 12.sp,
                            color = colors.labelSecondary
                        )
                    }
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = colors.blue
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Actualizar",
                            tint = colors.blue
                        )
                    }
                }
            }
        }

        // Últimos bloqueados (vista rápida)
        val blockedPreview = recentLogs.filter { it.blocked }.take(6)
        if (blockedPreview.isNotEmpty()) {
            item { IosSectionHeader("Últimos bloqueados") }
            item {
                IosGroupedCard {
                    blockedPreview.forEachIndexed { idx, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Block,
                                contentDescription = null,
                                tint = colors.red,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = entry.domain,
                                fontSize = 14.sp,
                                color = colors.labelPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = entry.timestamp,
                                fontSize = 12.sp,
                                color = colors.labelSecondary
                            )
                        }
                        if (idx < blockedPreview.lastIndex) IosDivider()
                    }
                }
            }
        }

        // Nota estilo iOS sobre DNS privado y VPN siempre activa
        item {
            IosSectionHeader("Recomendaciones")
            IosGroupedCard {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = colors.blue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Deja 'DNS privado' en Automático o Desactivado en los ajustes de Android para que el filtro funcione. En Ajustes > VPN puedes activar 'VPN siempre activa' para que no se cierre en segundo plano.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = colors.labelSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalIosColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardBackground)
            .padding(14.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.labelSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}

@Composable
private fun ActivityTab() {
    val colors = LocalIosColors.current
    val context = LocalContext.current
    val logs by AdBlockState.recentLogs.collectAsState()
    var filterOnlyBlocked by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedEntry by remember { mutableStateOf<DnsLogEntry?>(null) }

    val filtered = remember(logs, filterOnlyBlocked, searchQuery) {
        logs.filter { entry ->
            (!filterOnlyBlocked || entry.blocked) &&
                    (searchQuery.isBlank() || entry.domain.contains(searchQuery, ignoreCase = true))
        }
    }

    if (selectedEntry != null) {
        val entry = selectedEntry!!
        AlertDialog(
            onDismissRequest = { selectedEntry = null },
            title = { Text("Permitir dominio", fontWeight = FontWeight.Bold) },
            text = {
                Text("¿Agregar '${entry.domain}' a la lista blanca para que no vuelva a bloquearse?")
            },
            confirmButton = {
                TextButton(onClick = {
                    AdBlockState.addToWhitelist(context, entry.domain)
                    selectedEntry = null
                }) {
                    Text("Permitir", color = colors.blue)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedEntry = null }) {
                    Text("Cancelar", color = colors.labelSecondary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Actividad",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = colors.labelPrimary,
            modifier = Modifier.padding(top = 18.dp, bottom = 12.dp)
        )

        // Control segmentado estilo iOS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.secondaryFill)
                .padding(3.dp)
        ) {
            SegmentedButton(
                label = "Bloqueados",
                selected = filterOnlyBlocked,
                onClick = { filterOnlyBlocked = true },
                modifier = Modifier.weight(1f)
            )
            SegmentedButton(
                label = "Todas las consultas",
                selected = !filterOnlyBlocked,
                onClick = { filterOnlyBlocked = false },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar dominio...", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sin actividad registrada todavía",
                    color = colors.labelSecondary,
                    fontSize = 15.sp
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Text(
                        text = "Toca un dominio bloqueado para agregarlo a la lista blanca.",
                        fontSize = 12.sp,
                        color = colors.labelSecondary,
                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                    )
                }
                items(filtered) { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.cardBackground)
                            .clickable(enabled = entry.blocked) { selectedEntry = entry }
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (entry.blocked) Icons.Rounded.Block else Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = if (entry.blocked) colors.red else colors.green,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = entry.domain,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.labelPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = entry.timestamp,
                                fontSize = 11.sp,
                                color = colors.labelSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalIosColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.cardBackground else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = colors.labelPrimary
        )
    }
}

@Composable
private fun ListsTab() {
    val colors = LocalIosColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val enabledLists by AdBlockState.enabledLists.collectAsState()
    val whitelist by AdBlockState.whitelist.collectAsState()
    val isUpdating by AdBlockState.isUpdatingLists.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var newDomain by remember { mutableStateOf("") }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Agregar a lista blanca", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newDomain,
                    onValueChange = { newDomain = it },
                    placeholder = { Text("ejemplo.com") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    AdBlockState.addToWhitelist(context, newDomain)
                    newDomain = ""
                    showAddDialog = false
                }) { Text("Agregar", color = colors.blue) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancelar", color = colors.labelSecondary)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item {
            Text(
                text = "Listas",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = colors.labelPrimary,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
            )
        }

        item { IosSectionHeader("Listas de bloqueo") }

        item {
            IosGroupedCard {
                AdBlockState.availableLists.forEachIndexed { idx, list ->
                    val enabled = enabledLists.contains(list.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = list.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.labelPrimary
                            )
                            Text(
                                text = list.description,
                                fontSize = 12.sp,
                                color = colors.labelSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        CupertinoSwitch(
                            checked = enabled,
                            onCheckedChange = { AdBlockState.toggleFilterList(context, list.id) }
                        )
                    }
                    if (idx < AdBlockState.availableLists.lastIndex) IosDivider()
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            IosGroupedCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isUpdating) {
                            scope.launch { BlocklistManager.updateLists(context) }
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Descargando listas...", color = colors.blue, fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = colors.blue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Aplicar y actualizar ahora", color = colors.blue, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 6.dp, start = 16.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LISTA BLANCA (PERMITIDOS)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.labelSecondary
                )
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Agregar dominio",
                    tint = colors.blue,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { showAddDialog = true }
                )
            }
        }

        item {
            IosGroupedCard {
                if (whitelist.isEmpty()) {
                    Text(
                        text = "No hay dominios en lista blanca.",
                        fontSize = 14.sp,
                        color = colors.labelSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    val sorted = whitelist.sorted()
                    sorted.forEachIndexed { idx, domain ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = domain,
                                fontSize = 15.sp,
                                color = colors.labelPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Eliminar",
                                tint = colors.red,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { AdBlockState.removeFromWhitelist(context, domain) }
                            )
                        }
                        if (idx < sorted.lastIndex) IosDivider()
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsTab() {
    val colors = LocalIosColors.current
    val context = LocalContext.current

    val currentDns by AdBlockState.upstreamDnsIp.collectAsState()
    val excludedApps by AdBlockState.excludedApps.collectAsState()
    var showAppPicker by remember { mutableStateOf(false) }

    if (showAppPicker) {
        val installedApps = remember {
            val pm = context.packageManager
            pm.getInstalledApplications(0)
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != context.packageName }
                .map { it.packageName to pm.getApplicationLabel(it).toString() }
                .sortedBy { it.second.lowercase() }
        }

        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("Excluir aplicaciones", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.height(340.dp)) {
                    items(installedApps) { (pkg, label) ->
                        val isExcluded = excludedApps.contains(pkg)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { AdBlockState.toggleExcludedApp(context, pkg) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(pkg, fontSize = 11.sp, color = colors.labelSecondary)
                            }
                            CupertinoSwitch(
                                checked = isExcluded,
                                onCheckedChange = { AdBlockState.toggleExcludedApp(context, pkg) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) {
                    Text("Listo", color = colors.blue)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item {
            Text(
                text = "Ajustes",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = colors.labelPrimary,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
            )
        }

        item { IosSectionHeader("Servidor DNS Upstream") }

        item {
            IosGroupedCard {
                AdBlockState.upstreamDnsOptions.forEachIndexed { idx, dns ->
                    val selected = currentDns == dns.ip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { AdBlockState.setUpstreamDns(context, dns.ip) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Dns,
                            contentDescription = null,
                            tint = colors.blue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${dns.name} (${dns.ip})",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.labelPrimary
                            )
                            Text(
                                text = dns.subtitle,
                                fontSize = 12.sp,
                                color = colors.labelSecondary
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Seleccionado",
                                tint = colors.blue
                            )
                        }
                    }
                    if (idx < AdBlockState.upstreamDnsOptions.lastIndex) IosDivider()
                }
            }
        }

        item { IosSectionHeader("Sistema y exclusiones") }

        item {
            IosGroupedCard {
                SettingsRow(
                    title = "Apps excluidas de la VPN",
                    subtitle = "${excludedApps.size} seleccionadas (reinicia la VPN al cambiar)",
                    onClick = { showAppPicker = true }
                )
                IosDivider()
                SettingsRow(
                    title = "Abrir ajustes de VPN",
                    subtitle = "Activar 'VPN siempre activa'",
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
                        } catch (_: Exception) {
                        }
                    }
                )
                IosDivider()
                SettingsRow(
                    title = "Abrir ajustes de red",
                    subtitle = "Verificar DNS privado en Automático",
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                        } catch (_: Exception) {
                        }
                    }
                )
            }
        }

        item { IosSectionHeader("Datos") }

        item {
            IosGroupedCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { AdBlockState.resetStats(context) }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Restablecer estadísticas",
                        color = colors.red,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = LocalIosColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.labelPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = colors.labelSecondary
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = colors.labelSecondary
        )
    }
}
