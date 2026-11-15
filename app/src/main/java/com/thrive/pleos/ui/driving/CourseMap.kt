package com.thrive.pleos.ui.driving

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thrive.pleos.R
import com.thrive.pleos.ui.theme.*

/** Figma CourseMap 101:8595. Source SVGs are retained in assets/figma. */
@Composable
internal fun CourseMap(route: DemoRoute, modifier: Modifier = Modifier, driving: Boolean = false, height: Dp = ThriveSize.MapHeight) {
    BoxWithConstraints(modifier.fillMaxWidth().height(height).clip(ThriveShape.Panel)
        .background(Color.White.copy(alpha = .90f)).border(ThriveSize.Border, ThriveColors.Border, ThriveShape.Panel)
        .semantics { contentDescription = "${route.title} · 예시 지도. 실제 길 안내가 아닙니다." }) {
        val mapScale = minOf(1f, maxWidth / 720.dp)
        Image(painterResource(R.drawable.map_roads), null,
            Modifier.requiredSize(2400.dp * mapScale, 1600.dp * mapScale).align(Alignment.Center))
        Box(Modifier.requiredSize(620.dp, 340.dp).align(Alignment.Center).offset(y = 20.dp)
            .graphicsLayer { scaleX = mapScale; scaleY = mapScale }) {
            Image(painterResource(if (route == DemoRoute.Direct) R.drawable.map_route_direct else R.drawable.map_route_gentle), null,
                Modifier.offset(54.dp, 34.dp).size(512.dp, 272.dp))
            Image(painterResource(R.drawable.map_start), null, Modifier.offset(44.dp, 284.dp).size(32.dp))
            Image(painterResource(R.drawable.map_destination), null, Modifier.offset(542.dp, 22.dp).size(36.dp))
            val markerX = if (route == DemoRoute.Direct) 346.5.dp else 126.5.dp
            listOf("01" to 216.dp, "02" to 56.dp).forEach { (label, y) ->
                Surface(Modifier.offset(markerX, y).size(48.dp), shape = ThriveShape.Pill, color = Color.White,
                    border = BorderStroke(ThriveSize.SelectedBorder, ThriveColors.Text)) {
                    Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelSmall) }
                }
            }
            if (driving) Image(painterResource(R.drawable.map_current), null, Modifier.offset(32.dp, 150.dp).size(56.dp))
        }
        Text("${route.title} · 예시 지도",
            Modifier.padding(ThriveSpace.Xl).background(Color.White.copy(alpha = .90f), ThriveShape.Small)
                .padding(horizontal = ThriveSpace.Lg, vertical = ThriveSpace.Sm),
            style = MaterialTheme.typography.labelSmall, color = ThriveColors.Text)
    }
}
