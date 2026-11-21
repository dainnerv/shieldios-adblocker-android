package com.shieldios.adblocker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class IosColors(
    val bgTop: Color,
    val bgBottom: Color,
    val cardBackground: Color,
    val secondaryFill: Color,
    val labelPrimary: Color,
    val labelSecondary: Color,
    val separator: Color,
    val blue: Color,
    val blueSoft: Color,
    val green: Color,
    val greenSoft: Color,
    val red: Color,
    val redSoft: Color,
    val orange: Color,
    val orangeSoft: Color,
    val purple: Color,
    val tabBarBackground: Color,
    val cardShadow: Color
)

private val LightIosColors = IosColors(
    bgTop = Color(0xFFECF2FF),
    bgBottom = Color(0xFFF4F7FD),
    cardBackground = Color(0xFFFFFFFF),
    secondaryFill = Color(0xFFE9EEF8),
    labelPrimary = Color(0xFF0E1320),
    labelSecondary = Color(0xFF7B8499),
    separator = Color(0xFFE5EAF3),
    blue = Color(0xFF007AFF),
    blueSoft = Color(0xFFE6F1FF),
    green = Color(0xFF1DB954),
    greenSoft = Color(0xFFE5F8EC),
    red = Color(0xFFE53935),
    redSoft = Color(0xFFFDECEC),
    orange = Color(0xFFF5820D),
    orangeSoft = Color(0xFFFFF3E4),
    purple = Color(0xFF6C63FF),
    tabBarBackground = Color(0xFAFFFFFF),
    cardShadow = Color(0x181C3566)
)

private val DarkIosColors = IosColors(
    bgTop = Color(0xFF0A0D14),
    bgBottom = Color(0xFF000000),
    cardBackground = Color(0xFF171B24),
    secondaryFill = Color(0xFF232936),
    labelPrimary = Color(0xFFFFFFFF),
    labelSecondary = Color(0xFF8E96A8),
    separator = Color(0xFF2B3140),
    blue = Color(0xFF0A84FF),
    blueSoft = Color(0xFF13263E),
    green = Color(0xFF30D158),
    greenSoft = Color(0xFF122C1D),
    red = Color(0xFFFF453A),
    redSoft = Color(0xFF34181A),
    orange = Color(0xFFFF9F0A),
    orangeSoft = Color(0xFF332512),
    purple = Color(0xFF7D75FF),
    tabBarBackground = Color(0xFA12151D),
    cardShadow = Color(0x40000000)
)

val LocalIosColors = staticCompositionLocalOf { LightIosColors }

@Composable
fun ShieldIosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val iosColors = if (darkTheme) DarkIosColors else LightIosColors
    val materialScheme = if (darkTheme) {
        darkColorScheme(
            primary = iosColors.blue,
            background = iosColors.bgTop,
            surface = iosColors.cardBackground
        )
    } else {
        lightColorScheme(
            primary = iosColors.blue,
            background = iosColors.bgTop,
            surface = iosColors.cardBackground
        )
    }

    CompositionLocalProvider(LocalIosColors provides iosColors) {
        MaterialTheme(
            colorScheme = materialScheme,
            content = content
        )
    }
}

@Composable
fun IosSectionHeader(text: String, modifier: Modifier = Modifier) {
    val colors = LocalIosColors.current
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.labelSecondary,
        letterSpacing = 0.6.sp,
        modifier = modifier.padding(start = 6.dp, bottom = 8.dp, top = 16.dp)
    )
}

@Composable
fun IosSoftCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalIosColors.current
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = colors.cardShadow,
                spotColor = colors.cardShadow
            )
            .clip(shape)
            .background(colors.cardBackground),
        content = content
    )
}

@Composable
fun IosDivider() {
    val colors = LocalIosColors.current
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 0.6.dp,
        color = colors.separator
    )
}

@Composable
fun CupertinoSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalIosColors.current
    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.green else colors.secondaryFill,
        label = "switchTrack"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "switchThumb"
    )

    Box(
        modifier = modifier
            .width(51.dp)
            .height(31.dp)
            .clip(CircleShape)
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(27.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
