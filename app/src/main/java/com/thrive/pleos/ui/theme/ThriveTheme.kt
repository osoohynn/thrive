package com.thrive.pleos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thrive.pleos.R

/** Figma 101:8409 palette; shared by every driving screen. */
object ThriveColors {
    val Background = Color.White
    val Brand = Color(0xFF0F3281)
    val Text = Color(0xFF0B1424)
    val TextSecondary = Color(0xFF1C3C52)
    val Accent = Brand
    val Mint = Color(0xFF93E8EE)
    val Glass = Color.White.copy(alpha = 0.86f)
    val NavigationSurface = Color.Transparent
    val NavigationSelection = Color(0xFFE6E8EB).copy(alpha = 0.72f)
    val GlassSubtle = Brand.copy(alpha = 0.04f)
    val GlassSelected = Mint.copy(alpha = 0.35f)
    val ReadingSurface = Color.White.copy(alpha = 0.92f)
    val ReadingSurfaceSelected = Mint.copy(alpha = 0.18f)
    val ControlBorder = Text.copy(alpha = 0.14f)
    val Border = Text.copy(alpha = 0.08f)
    val BorderStrong = Brand
    val Scrim = Color.Black.copy(alpha = 0.40f)
    val Focus = Brand
}

object ThriveSpace {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Panel = 28.dp
    val Xxxl = 32.dp
    val Section = 40.dp
}

object ThriveShape {
    val Small = RoundedCornerShape(8.dp)
    val Control = RoundedCornerShape(16.dp)
    val Navigation = RoundedCornerShape(28.dp)
    val Panel = RoundedCornerShape(20.dp)
    val Pill = RoundedCornerShape(50)
}

object ThriveSize {
    val Border = 1.dp
    val FocusBorder = 2.dp
    val SelectedBorder = 2.dp
    val TouchTarget = 64.dp
    val InputMinHeight = 88.dp
    val CardMinHeight = 320.dp
    val ActivityMinHeight = 128.dp
    val ProgressHeight = 6.dp
    val WideBreakpoint = 760.dp
    val ContentMaxWidth = 1480.dp
    val RailWidth = 128.dp
    val RailIcon = 50.dp
    val RailGap = 10.dp
    val PageTop = 35.dp
    val PageStart = 28.dp
    val RailContentGap = 47.dp
    val PageEnd = 41.dp
    val MapHeight = 440.dp
}

@OptIn(ExperimentalTextApi::class)
private val Pretendard = FontFamily(
    *listOf(400, 500, 600, 700).map { weight ->
        Font(
            resId = R.font.pretendard_variable,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    }.toTypedArray(),
)

private fun type(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) = TextStyle(
    fontFamily = Pretendard,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

private val ThriveTypography = Typography(
    displayLarge = type(72, 84, FontWeight.Medium),
    displayMedium = type(64, 76, FontWeight.Medium),
    displaySmall = type(60, 80, FontWeight.Bold),
    headlineLarge = type(56, 72, FontWeight.SemiBold),
    headlineMedium = type(48, 64, FontWeight.Bold),
    headlineSmall = type(40, 52, FontWeight.SemiBold, tracking = -.4f),
    titleLarge = type(40, 52, FontWeight.SemiBold),
    titleMedium = type(32, 40, FontWeight.SemiBold, tracking = -.2f),
    titleSmall = type(28, 36, FontWeight.SemiBold, tracking = -.1f),
    bodyLarge = type(28, 36),
    bodyMedium = type(24, 32),
    bodySmall = type(22, 30),
    labelLarge = type(24, 28, FontWeight.SemiBold),
    labelMedium = type(24, 32, FontWeight.SemiBold),
    labelSmall = type(20, 24, FontWeight.SemiBold),
)

@Composable
fun ThriveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = ThriveColors.Brand,
            onPrimary = Color.White,
            secondary = ThriveColors.Brand,
            onSecondary = Color.White,
            secondaryContainer = ThriveColors.GlassSelected,
            onSecondaryContainer = ThriveColors.Text,
            background = ThriveColors.Background,
            onBackground = ThriveColors.Text,
            surface = Color.White,
            onSurface = ThriveColors.Text,
            surfaceVariant = ThriveColors.ReadingSurfaceSelected,
            onSurfaceVariant = ThriveColors.TextSecondary,
            surfaceTint = Color.Transparent,
            inverseSurface = ThriveColors.Text,
            inverseOnSurface = Color.White,
            outline = ThriveColors.ControlBorder,
            outlineVariant = ThriveColors.Border,
            scrim = ThriveColors.Scrim,
            surfaceDim = ThriveColors.Background,
            surfaceBright = Color.White,
            surfaceContainerLowest = ThriveColors.Background,
            surfaceContainerLow = ThriveColors.Background,
            surfaceContainer = ThriveColors.ReadingSurface,
            surfaceContainerHigh = ThriveColors.ReadingSurfaceSelected,
            surfaceContainerHighest = ThriveColors.GlassSelected,
        ),
        typography = ThriveTypography,
        shapes = Shapes(
            extraSmall = ThriveShape.Small,
            small = ThriveShape.Small,
            medium = ThriveShape.Panel,
            large = ThriveShape.Panel,
            extraLarge = ThriveShape.Panel,
        ),
        content = content,
    )
}
