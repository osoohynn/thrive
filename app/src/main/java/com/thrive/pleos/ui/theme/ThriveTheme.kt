package com.thrive.pleos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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

/** Option 3, Figma 2:251. See docs/design-system.md for source and adapted values. */
object ThriveColors {
    val Background = Color(0xFF0B1424)
    val Brand = Color(0xFF0F3281)
    val Text = Color.White
    val TextSecondary = Color(0xFFD5DEEA)
    val Accent = Color(0xFF93E8EE)
    val Glass = Color.White.copy(alpha = 0.10f)
    val GlassSubtle = Color.White.copy(alpha = 0.05f)
    val GlassSelected = Color.White.copy(alpha = 0.16f)
    val ReadingSurface = Color(0xEB142238)
    val ReadingSurfaceSelected = Color(0xF01C3C52)
    val ControlBorder = Color.White.copy(alpha = 0.44f)
    val Border = Color.White.copy(alpha = 0.18f)
    val BorderStrong = Color.White.copy(alpha = 0.60f)
    val Scrim = Color.Black.copy(alpha = 0.64f)
    val Focus = Color.White
}

object ThriveSpace {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Xxxl = 32.dp
    val Section = 40.dp
}

object ThriveShape {
    val Small = RoundedCornerShape(8.dp)
    val Panel = RoundedCornerShape(20.dp)
    val Pill = RoundedCornerShape(50)
}

object ThriveSize {
    val Border = 1.dp
    val FocusBorder = 2.dp
    val TouchTarget = 64.dp
    val InputMinHeight = 88.dp
    val CardMinHeight = 320.dp
    val ActivityMinHeight = 128.dp
    val ProgressHeight = 6.dp
    val WideBreakpoint = 760.dp
    val ContentMaxWidth = 1200.dp
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

private fun type(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = Pretendard,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
)

private val ThriveTypography = Typography(
    displayLarge = type(72, 84, FontWeight.Medium),
    displayMedium = type(64, 76, FontWeight.Medium),
    displaySmall = type(56, 68, FontWeight.SemiBold),
    headlineLarge = type(56, 72, FontWeight.SemiBold),
    headlineMedium = type(48, 64, FontWeight.SemiBold),
    headlineSmall = type(40, 52, FontWeight.SemiBold),
    titleLarge = type(40, 52, FontWeight.SemiBold),
    titleMedium = type(32, 44, FontWeight.SemiBold),
    titleSmall = type(28, 38, FontWeight.SemiBold),
    bodyLarge = type(28, 40),
    bodyMedium = type(24, 34),
    bodySmall = type(22, 32),
    labelLarge = type(26, 36, FontWeight.SemiBold),
    labelMedium = type(22, 30, FontWeight.Medium),
    labelSmall = type(20, 28, FontWeight.Medium),
)

@Composable
fun ThriveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ThriveColors.Text,
            onPrimary = ThriveColors.Background,
            primaryContainer = ThriveColors.Brand,
            onPrimaryContainer = ThriveColors.Text,
            inversePrimary = ThriveColors.Brand,
            secondary = ThriveColors.Accent,
            onSecondary = ThriveColors.Background,
            secondaryContainer = ThriveColors.Brand,
            onSecondaryContainer = ThriveColors.Text,
            tertiary = ThriveColors.Accent,
            onTertiary = ThriveColors.Background,
            tertiaryContainer = ThriveColors.Brand,
            onTertiaryContainer = ThriveColors.Text,
            background = ThriveColors.Background,
            onBackground = ThriveColors.Text,
            surface = ThriveColors.Background,
            onSurface = ThriveColors.Text,
            surfaceVariant = ThriveColors.Brand,
            onSurfaceVariant = ThriveColors.TextSecondary,
            surfaceTint = Color.Transparent,
            inverseSurface = ThriveColors.Text,
            inverseOnSurface = ThriveColors.Background,
            outline = ThriveColors.Border,
            outlineVariant = ThriveColors.Border,
            scrim = ThriveColors.Scrim,
            surfaceDim = ThriveColors.Background,
            surfaceBright = ThriveColors.Brand,
            surfaceContainerLowest = ThriveColors.Background,
            surfaceContainerLow = ThriveColors.Background,
            surfaceContainer = ThriveColors.Background,
            surfaceContainerHigh = ThriveColors.Brand,
            surfaceContainerHighest = ThriveColors.Brand,
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
