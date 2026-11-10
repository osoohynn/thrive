package com.thrive.pleos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF101813)
private val Panel = Color(0xFF1B261E)
private val Accent = Color(0xFFD2EC9C)
private val Muted = Color(0xFFA5B0A4)
private val Outline = Color(0xFF354236)

private data class RestOption(val title: Int, val duration: Int, val description: Int, val mark: String)
private val restOptions = listOf(
    RestOption(R.string.breathe, R.string.three_minutes, R.string.breathe_description, "01"),
    RestOption(R.string.stretch, R.string.five_minutes, R.string.stretch_description, "02"),
    RestOption(R.string.unwind, R.string.ten_minutes, R.string.unwind_description, "03"),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ThriveApp() }
    }
}

@Composable
private fun ThriveApp() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var resting by rememberSaveable { mutableStateOf(false) }
    val option = restOptions[selected]

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Background,
            background = Background, surface = Panel,
            onSurface = Color(0xFFF2F4EB), onSurfaceVariant = Muted, outline = Outline,
        ),
    ) {
        Surface(color = Background, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.safeDrawingPadding()) {
                val wide = maxWidth >= 760.dp
                Column(
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(if (wide) 32.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("thrive", fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp)
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(R.string.demo_data), color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.width(16.dp))
                        Surface(color = Panel, shape = RoundedCornerShape(50)) {
                            Text(stringResource(R.string.parked), Modifier.padding(horizontal = 14.dp, vertical = 9.dp), color = Accent, fontSize = 14.sp)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.home_title), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.home_subtitle), color = Muted, fontSize = 16.sp)
                    }
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            RestCard(option, resting, { resting = !resting }, Modifier.weight(1.7f))
                            VehicleCard(Modifier.weight(1f))
                        }
                    } else {
                        RestCard(option, resting, { resting = !resting })
                        VehicleCard()
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.activity_heading), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(R.string.activity_hint), color = Muted, fontSize = 13.sp)
                    }
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            restOptions.forEachIndexed { index, item ->
                                ActivityCard(item, selected == index, {
                                    selected = index
                                    resting = false
                                }, Modifier.weight(1f))
                            }
                        }
                    } else {
                        restOptions.forEachIndexed { index, item ->
                            ActivityCard(item, selected == index, {
                                selected = index
                                resting = false
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RestCard(option: RestOption, resting: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().heightIn(min = 280.dp), shape = RoundedCornerShape(24.dp), color = Accent) {
        Box {
            PlantArtwork(Modifier.matchParentSize())
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (resting) R.string.resting_label else R.string.recommended_label), color = Background.copy(alpha = 0.65f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(option.title), color = Background, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(if (resting) R.string.resting_description else option.description), Modifier.fillMaxWidth(0.7f), color = Background.copy(alpha = 0.75f), fontSize = 16.sp, lineHeight = 25.sp)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onToggle,
                    modifier = Modifier.heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Background, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                ) {
                    Text(stringResource(if (resting) R.string.finish_rest else R.string.start_rest), fontSize = 16.sp)
                    Text("  ·  ${stringResource(option.duration)}", color = Accent, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun VehicleCard(modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().heightIn(min = 280.dp), shape = RoundedCornerShape(24.dp), color = Panel) {
        Column(Modifier.padding(26.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.vehicle_heading), fontSize = 15.sp, color = Muted)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("78", fontSize = 52.sp, fontWeight = FontWeight.Medium, letterSpacing = (-2).sp, lineHeight = 56.sp)
                Text("%", Modifier.padding(bottom = 7.dp), fontSize = 22.sp, color = Muted)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.battery_label), Modifier.padding(bottom = 9.dp), fontSize = 13.sp, color = Muted)
            }
            // Static mock values; no vehicle connection or charging calculation.
            Box(Modifier.fillMaxWidth().height(6.dp).background(Outline, RoundedCornerShape(50))) {
                Box(Modifier.fillMaxWidth(0.78f).fillMaxHeight().background(Accent, RoundedCornerShape(50)))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.range_label), fontSize = 14.sp, color = Muted)
                Text("324 km", fontSize = 15.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.cabin_label), fontSize = 14.sp, color = Muted)
                Text("22°", fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun ActivityCard(option: RestOption, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onSelect,
        selected = selected,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Color(0xFF293727) else Panel,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Accent.copy(alpha = 0.6f) else Outline),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(option.mark, color = if (selected) Accent else Muted, fontSize = 14.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(stringResource(option.title), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(option.duration), color = Muted, fontSize = 14.sp)
            }
            Text(if (selected) "●" else "↗", color = if (selected) Accent else Muted, fontSize = 18.sp)
        }
    }
}

@Composable
private fun PlantArtwork(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stem = Color(0xFF4B693C)
        drawCircle(Color(0xFFE4F4BE), radius = h * 0.25f, center = Offset(w * 0.90f, h * 0.32f))
        drawOval(Color(0xFFB4D17E), topLeft = Offset(w * 0.68f, h * 0.86f), size = Size(w * 0.5f, h * 0.28f))
        drawPath(Path().apply {
            moveTo(w * 0.87f, h * 0.91f)
            cubicTo(w * 0.83f, h * 0.66f, w * 0.86f, h * 0.45f, w * 0.92f, h * 0.31f)
        }, stem, style = Stroke(width = 4.dp.toPx()))
        drawPath(Path().apply {
            moveTo(w * 0.85f, h * 0.68f)
            cubicTo(w * 0.72f, h * 0.66f, w * 0.73f, h * 0.45f, w * 0.73f, h * 0.43f)
            cubicTo(w * 0.84f, h * 0.44f, w * 0.88f, h * 0.54f, w * 0.85f, h * 0.68f)
            close()
        }, stem)
        drawPath(Path().apply {
            moveTo(w * 0.86f, h * 0.55f)
            cubicTo(w * 0.86f, h * 0.36f, w * 0.98f, h * 0.33f, w * 0.99f, h * 0.35f)
            cubicTo(w * 0.99f, h * 0.48f, w * 0.94f, h * 0.56f, w * 0.86f, h * 0.55f)
            close()
        }, Color(0xFF789353))
    }
}

@Preview(name = "차량 가로 화면", widthDp = 1100, heightDp = 700)
@Preview(name = "멀티 윈도우", widthDp = 480, heightDp = 800)
@Composable
private fun ThrivePreview() {
    ThriveApp()
}
