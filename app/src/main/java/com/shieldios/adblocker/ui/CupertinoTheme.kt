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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class IosColors(
    val background: Color,
    val cardBackground: Color,
    val secondaryFill: Color,
    val labelPrimary: Color,
    val labelSecondary: Color,
    val separator: Color,
    val blue: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val tabBarBackground: Color
)

private val LightIosColors = IosColors(
    background = Color(0xFFF2F2F7),
    cardBackground = Color(0xFFFFFFFF),
    secondaryFill = Color(0xFFE5E5EA),
    labelPrimary = Color(0xFF000000),
    labelSecondary = Color(0xFF8E8E93),
    separator = Color(0xFFD1D1D6),
    blue = Color(0xFF007AFF),
    green = Color(0xFF34C759),
    red = Color(0xFFFF3B30),
    orange = Color(0xFFFF9500),
    tabBarBackground = Color(0xF5F9F9F9)
)

private val DarkIosColors = IosColors(
    background = Color(0xFF000000),
    cardBackground = Color(0xFF1C1C1E),
    secondaryFill = Color(0xFF2C2C2E),
    labelPrimary = Color(0xFFFFFFFF),
    labelSecondary = Color(0xFF8E8E93),
    separator = Color(0xFF38383A),
    blue = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A),
    tabBarBackground = Color(0xF5161616)
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
            background = iosColors.background,
            surface = iosColors.cardBackground
        )
    } else {
        lightColorScheme(
            primary = iosColors.blue,
            background = iosColors.background,
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
        fontWeight = FontWeight.Medium,
        color = colors.labelSecondary,
        letterSpacing = 0.5.sp,
        modifier = modifier.padding(start = 16.dp, bottom = 6.dp, top = 16.dp)
    )
}

@Composable
fun IosGroupedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalIosColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardBackground),
        content = content
    )
}

@Composable
fun IosDivider() {
    val colors = LocalIosColors.current
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 0.5.dp,
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
