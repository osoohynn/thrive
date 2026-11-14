package com.thrive.pleos.ui.driving

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.thrive.pleos.ui.theme.*

/** Original concept artwork. No map tiles, real coordinates, or route guidance. */
@Composable
internal fun CourseMap(route: DemoRoute, modifier: Modifier = Modifier, driving: Boolean = false) {
    Box(modifier.fillMaxWidth().heightIn(min = ThriveSize.CardMinHeight).clip(ThriveShape.Panel).background(ThriveColors.Background)) {
        Canvas(Modifier.matchParentSize().semantics { contentDescription = "모의 지도. ${route.title}. 실제 길 안내가 아닙니다." }) {
            scale(size.width / 860f, size.height / 600f, pivot = Offset.Zero) {
                for (x in 0..8) for (y in 0..5) {
                    drawRoundRect(ThriveColors.GlassSubtle, Offset(x * 110f + 12f, y * 110f + 12f), Size(84f, 83f))
                }
                for (x in listOf(110f, 240f, 430f, 620f, 760f)) {
                    drawLine(ThriveColors.Border, Offset(x, 0f), Offset(x, 600f), strokeWidth = 12f)
                }
                for (y in listOf(110f, 190f, 330f, 430f, 540f)) {
                    drawLine(ThriveColors.Border, Offset(0f, y), Offset(860f, y), strokeWidth = 12f)
                }
                val alternative = Path().apply { moveTo(240f, 520f); lineTo(240f, 330f); lineTo(620f, 330f); lineTo(620f, 110f) }
                drawPath(alternative, ThriveColors.BorderStrong, style = Stroke(5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))))
                val selected = if (route == DemoRoute.Direct) alternative else Path().apply {
                    moveTo(240f, 520f); lineTo(240f, 430f); lineTo(430f, 430f); lineTo(430f, 190f); lineTo(620f, 190f); lineTo(620f, 110f)
                }
                drawPath(selected, ThriveColors.Accent.copy(alpha = .12f), style = Stroke(28f, cap = StrokeCap.Round))
                drawPath(selected, ThriveColors.Accent, style = Stroke(8f, cap = StrokeCap.Round))
                drawCircle(ThriveColors.Text, 13f, Offset(240f, 520f))
                drawCircle(ThriveColors.Accent, 20f, Offset(620f, 110f))
                drawCircle(ThriveColors.Background, 9f, Offset(620f, 110f))
                if (driving) {
                    drawCircle(ThriveColors.Accent.copy(alpha = .2f), 40f, Offset(430f, 350f))
                    val marker = Path().apply { moveTo(430f, 327f); lineTo(448f, 370f); lineTo(430f, 360f); lineTo(412f, 370f); close() }
                    drawPath(marker, ThriveColors.Text)
                }
            }
        }
        Text("성수동 · 모의 지도",
            Modifier.align(Alignment.TopStart).padding(ThriveSpace.Md)
                .background(ThriveColors.ReadingSurface, ThriveShape.Small)
                .padding(horizontal = ThriveSpace.Lg, vertical = ThriveSpace.Sm),
            style = MaterialTheme.typography.bodySmall, color = ThriveColors.TextSecondary)
    }
}
