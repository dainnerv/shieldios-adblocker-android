package com.shieldios.adblocker.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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

    LaunchedEffect(Unit) {
        AdBlockState.pushUiSnapshot()
    }

    Scaffold(
        containerColor = Color.Transparent,
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
                .background(
                    Brush.verticalGradient(
                        colors = listOf(colors.bgTop, colors.bgBottom)
                    )
                )
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (selectedTab) {
                0 -> ProtectionTab(
                    onToggleVpn = onToggleVpn,
                    onOpenSettings = { selectedTab = 3 },
                    onOpenActivity = { selectedTab = 1 }
                )
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
        Triple("Listas", Icons.Rounded.FormatListBulleted, 2),
        Triple("Ajustes", Icons.Rounded.Settings, 3)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.tabBarBackground)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = colors.separator)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            items.forEach { (label, icon, index) ->
                val isSelected = selectedTab == index
                val tint = if (isSelected) colors.blue else colors.labelSecondary
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) colors.blueSoft else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = tint
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtectionTab(
    onToggleVpn: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenActivity: () -> Unit
) {
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
    val hourlyBlocked by AdBlockState.hourlyBlocked.collectAsState()

    val numberFormat = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }
    val blockPercent = if (totalQueries > 0) ((blockedCount * 100.0) / totalQueries) else 0.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Encabezado estilo prototipo: título + subtítulo + botón circular de ajustes
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Protección",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.labelPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Bloquea anuncios, rastreadores y dominios no deseados",
                        fontSize = 14.sp,
                        color = colors.labelSecondary,
                        lineHeight = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(42.dp)
                        .shadow(6.dp, CircleShape, ambientColor = colors.cardShadow, spotColor = colors.cardShadow)
                        .clip(CircleShape)
                        .background(colors.cardBackground)
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Ajustes",
                        tint = colors.labelSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Tarjeta principal Hero: anillo circular verde a la izquierda + 3 indicadores a la derecha
        item {
            IosSoftCard(cornerRadius = 26.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Anillo circular interactivo (sin animación 120fps para ahorrar batería)
                    val ringAccent = if (isRunning) colors.green else colors.labelSecondary
                    Box(
                        modifier = Modifier
                            .size(154.dp)
                            .clip(CircleShape)
                            .background(ringAccent.copy(alpha = 0.14f))
                            .clickable { onToggleVpn() }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(
                                    width = 9.dp,
                                    brush = Brush.verticalGradient(
                                        if (isRunning) listOf(Color(0xFF4CD964), Color(0xFF16A34A))
                                        else listOf(Color(0xFFB0B7C6), Color(0xFF8E96A8))
                                    ),
                                    shape = CircleShape
                                )
                                .padding(12.dp)
                                .clip(CircleShape)
                                .background(colors.cardBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.VerifiedUser,
                                    contentDescription = "Estado de protección",
                                    tint = ringAccent,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Protección",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ringAccent
                                )
                                Text(
                                    text = if (isRunning) "ACTIVA" else "INACTIVA",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ringAccent
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // 3 filas de estado a la derecha
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        HeroStatusRow(
                            icon = Icons.Rounded.Shield,
                            title = "VPN local DNS",
                            subtitle = if (isRunning) "Activa" else "En pausa",
                            active = isRunning
                        )
                        HeroStatusRow(
                            icon = Icons.Rounded.Lock,
                            title = "Bloqueo de anuncios",
                            subtitle = if (isRunning) "Habilitado" else "Deshabilitado",
                            active = isRunning
                        )
                        HeroStatusRow(
                            icon = Icons.Rounded.Eco,
                            title = "Sin rastreo",
                            subtitle = if (isRunning) "Privacidad total" else "Sin filtrar",
                            active = isRunning
                        )
                    }
                }
            }
        }

        // Cuadrícula 2x2 de estadísticas (idéntica al prototipo)
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    icon = Icons.Rounded.Block,
                    iconTint = colors.red,
                    iconBg = colors.redSoft,
                    label = "Bloqueados",
                    value = numberFormat.format(blockedCount),
                    valueColor = colors.red,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    icon = Icons.Rounded.Search,
                    iconTint = colors.blue,
                    iconBg = colors.blueSoft,
                    label = "Consultas",
                    value = numberFormat.format(totalQueries),
                    valueColor = colors.blue,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    icon = Icons.Rounded.Layers,
                    iconTint = colors.orange,
                    iconBg = colors.orangeSoft,
                    label = "Dominios",
                    value = numberFormat.format(domainCount),
                    valueColor = colors.orange,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    icon = Icons.Rounded.BarChart,
                    iconTint = colors.green,
                    iconBg = colors.greenSoft,
                    label = "Tasa de bloqueo",
                    value = String.format(Locale.getDefault(), "%.1f%%", blockPercent),
                    valueColor = colors.green,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Tarjeta de gráfica "Actividad de bloqueo" (Últimas 24 horas)
        item {
            Spacer(modifier = Modifier.height(14.dp))
            BlockingActivityChartCard(hourlyBlocked = hourlyBlocked)
        }

        // Tarjeta "Actualizar listas de anuncios"
        item {
            Spacer(modifier = Modifier.height(14.dp))
            IosSoftCard(cornerRadius = 20.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isUpdating) {
                            scope.launch { BlocklistManager.updateLists(context) }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.blueSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.2.dp,
                                color = colors.blue
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Actualizar",
                                tint = colors.blue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Actualizar listas de anuncios",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.labelPrimary
                        )
                        Text(
                            text = lastUpdated,
                            fontSize = 13.sp,
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
        }

        // Encabezado "ÚLTIMOS BLOQUEADOS" + "Ver todo"
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ÚLTIMOS BLOQUEADOS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.labelSecondary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Ver todo",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.blue,
                    modifier = Modifier.clickable { onOpenActivity() }
                )
            }
        }

        val blockedPreview = recentLogs.filter { it.blocked }.take(5)
        item {
            IosSoftCard(cornerRadius = 20.dp) {
                if (blockedPreview.isEmpty()) {
                    Text(
                        text = "Activa la protección para ver los dominios bloqueados en tiempo real.",
                        fontSize = 13.sp,
                        color = colors.labelSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    blockedPreview.forEachIndexed { idx, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(colors.redSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Block,
                                    contentDescription = null,
                                    tint = colors.red,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
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
                                fontSize = 12.sp,
                                color = colors.labelSecondary
                            )
                        }
                        if (idx < blockedPreview.lastIndex) IosDivider()
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeroStatusRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    active: Boolean
) {
    val colors = LocalIosColors.current
    val tint = if (active) colors.green else colors.labelSecondary
    val bg = if (active) colors.greenSoft else colors.secondaryFill

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = colors.labelSecondary
            )
        }
    }
}

@Composable
private fun MetricTile(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalIosColors.current
    IosSoftCard(modifier = modifier, cornerRadius = 20.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BlockingActivityChartCard(hourlyBlocked: List<Int>) {
    val colors = LocalIosColors.current
    val numberFormat = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }

    // Agrupar 24 horas en 8 puntos suaves (bloques de 3 horas) para curva limpia
    val points = remember(hourlyBlocked) {
        val buckets = if (hourlyBlocked.size == 24) hourlyBlocked else List(24) { 0 }
        List(8) { idx ->
            buckets[idx * 3] + buckets[idx * 3 + 1] + buckets[idx * 3 + 2]
        }
    }
    val maxVal = (points.maxOrNull() ?: 0).coerceAtLeast(10)
    val peakIndex = points.indices.maxByOrNull { points[it] } ?: 0
    val peakValue = points[peakIndex]

    IosSoftCard(cornerRadius = 24.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Actividad de bloqueo",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.labelPrimary
                )
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.secondaryFill)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Últimas 24 horas",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.labelPrimary
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = colors.labelSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Etiqueta de pico máximo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.purple)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "• ${numberFormat.format(peakValue)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
            ) {
                // Eje Y
                Column(
                    modifier = Modifier
                        .width(36.dp)
                        .height(112.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(numberFormat.format(maxVal), fontSize = 10.sp, color = colors.labelSecondary)
                    Text(numberFormat.format(maxVal * 2 / 3), fontSize = 10.sp, color = colors.labelSecondary)
                    Text(numberFormat.format(maxVal / 3), fontSize = 10.sp, color = colors.labelSecondary)
                    Text("0", fontSize = 10.sp, color = colors.labelSecondary)
                }

                // Curva Bezier suave
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(112.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val stepX = w / (points.size - 1).coerceAtLeast(1)

                    // Líneas guía horizontales
                    for (i in 0..3) {
                        val y = h * (i / 3f)
                        drawLine(
                            color = colors.separator,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val coords = points.mapIndexed { idx, value ->
                        val ratio = (value.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                        Offset(idx * stepX, h - (ratio * (h * 0.88f)) - 4f)
                    }

                    val linePath = Path().apply {
                        moveTo(coords.first().x, coords.first().y)
                        for (i in 0 until coords.lastIndex) {
                            val p0 = coords[i]
                            val p1 = coords[i + 1]
                            val cx = (p0.x + p1.x) / 2f
                            cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                        }
                    }

                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(coords.last().x, h)
                        lineTo(coords.first().x, h)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.purple.copy(alpha = 0.28f),
                                colors.purple.copy(alpha = 0.02f)
                            )
                        )
                    )

                    drawPath(
                        path = linePath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF4F7BFF), Color(0xFF8B5CF6))
                        ),
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Línea punteada vertical y punto en el pico
                    val peakCoord = coords[peakIndex]
                    drawLine(
                        color = colors.purple.copy(alpha = 0.45f),
                        start = Offset(peakCoord.x, peakCoord.y),
                        end = Offset(peakCoord.x, h),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    )
                    drawCircle(
                        color = colors.purple,
                        radius = 5.5.dp.toPx(),
                        center = peakCoord
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.8.dp.toPx(),
                        center = peakCoord
                    )
                }
            }

            // Etiquetas eje X
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("00:00", "04:00", "08:00", "12:00", "16:00", "20:00").forEach { label ->
                    Text(text = label, fontSize = 10.sp, color = colors.labelSecondary)
                }
            }
        }
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
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
            shape = RoundedCornerShape(14.dp),
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
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Text(
                        text = "Toca un dominio bloqueado para agregarlo a la lista blanca.",
                        fontSize = 12.sp,
                        color = colors.labelSecondary,
                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                    )
                }
                items(filtered) { entry ->
                    IosSoftCard(
                        modifier = Modifier.padding(vertical = 4.dp),
                        cornerRadius = 14.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = entry.blocked) { selectedEntry = entry }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
            .clip(RoundedCornerShape(10.dp))
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
            IosSoftCard {
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
                                fontWeight = FontWeight.SemiBold,
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
            IosSoftCard(cornerRadius = 18.dp) {
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
                    .padding(top = 18.dp, bottom = 8.dp, start = 6.dp, end = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LISTA BLANCA (PERMITIDOS)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
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
            IosSoftCard {
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
    val cacheHits by AdBlockState.cacheHits.collectAsState()
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

        item { IosSectionHeader("Ahorro de energía") }

        item {
            IosSoftCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Caché DNS local activo",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.labelPrimary
                        )
                        Text(
                            text = "Consultas resueltas sin usar radio móvil: $cacheHits",
                            fontSize = 12.sp,
                            color = colors.labelSecondary
                        )
                    }
                }
            }
        }

        item { IosSectionHeader("Servidor DNS Upstream") }

        item {
            IosSoftCard {
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
            IosSoftCard {
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
            IosSoftCard(cornerRadius = 18.dp) {
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
                        fontWeight = FontWeight.SemiBold
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
